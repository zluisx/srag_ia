package br.edu.unifsa.srag.service;

import br.edu.unifsa.srag.domain.Perfil;
import br.edu.unifsa.srag.domain.RegistroAnalise;
import br.edu.unifsa.srag.domain.Usuario;
import br.edu.unifsa.srag.dto.Dtos.PageResponse;
import br.edu.unifsa.srag.dto.Dtos.RegistroRequest;
import br.edu.unifsa.srag.dto.Dtos.RegistroResponse;
import br.edu.unifsa.srag.exception.ApiException;
import br.edu.unifsa.srag.modelo.EsquemaEntradaValidator;
import br.edu.unifsa.srag.repository.PredicaoRepository;
import br.edu.unifsa.srag.repository.RegistroAnaliseRepository;
import br.edu.unifsa.srag.security.UsuarioLogado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Regras de acesso:
 *  - leitura: dono do registro ou GESTOR (auditor);
 *  - alteração, exclusão e predição: somente o dono.
 */
@Service
public class RegistroService {

    private final RegistroAnaliseRepository registros;
    private final PredicaoRepository predicoes;
    private final UsuarioLogado logado;
    private final EsquemaEntradaValidator validador;
    private final JsonUtil json;

    public RegistroService(RegistroAnaliseRepository registros, PredicaoRepository predicoes,
                           UsuarioLogado logado, EsquemaEntradaValidator validador, JsonUtil json) {
        this.registros = registros;
        this.predicoes = predicoes;
        this.logado = logado;
        this.validador = validador;
        this.json = json;
    }

    @Transactional
    public RegistroResponse criar(RegistroRequest req) {
        Usuario u = logado.atual();
        exigirDadosValidos(req.dados());
        String id = req.identificador().trim();
        if (registros.existsByIdentificador(id)) {
            throw ApiException.conflito("Já existe um registro com este identificador");
        }
        RegistroAnalise r = new RegistroAnalise();
        r.setIdentificador(id);
        r.setDadosJson(json.paraJson(req.dados()));
        r.setCriadoPor(u);
        return paraDto(registros.save(r));
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistroResponse> listar(Pageable pageable) {
        Usuario u = logado.atual();
        Page<RegistroAnalise> pagina = u.getPerfil() == Perfil.GESTOR
                ? registros.findAll(pageable)
                : registros.findByCriadoPorId(u.getId(), pageable);
        return PageResponse.de(pagina.map(this::paraDto));
    }

    @Transactional(readOnly = true)
    public RegistroResponse obter(Long id) {
        return paraDto(buscarParaLeitura(id));
    }

    @Transactional
    public RegistroResponse atualizar(Long id, RegistroRequest req) {
        RegistroAnalise r = buscarParaEscrita(id);
        exigirDadosValidos(req.dados());
        String novoId = req.identificador().trim();
        if (!novoId.equals(r.getIdentificador()) && registros.existsByIdentificador(novoId)) {
            throw ApiException.conflito("Já existe um registro com este identificador");
        }
        r.setIdentificador(novoId);
        r.setDadosJson(json.paraJson(req.dados()));
        r.setAtualizadoEm(Instant.now());
        return paraDto(r);
    }

    @Transactional
    public void excluir(Long id) {
        RegistroAnalise r = buscarParaEscrita(id);
        if (predicoes.countByRegistroId(id) > 0) {
            // O histórico de predições deve ser preservado (requisito do trabalho)
            throw ApiException.conflito("O registro tem histórico de predições e não pode ser excluído");
        }
        registros.delete(r);
    }

    // ---------- usados também pelo PredicaoService ----------

    public RegistroAnalise buscarParaLeitura(Long id) {
        Usuario u = logado.atual();
        RegistroAnalise r = registros.findById(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Registro não encontrado"));
        if (u.getPerfil() != Perfil.GESTOR && !r.getCriadoPor().getId().equals(u.getId())) {
            throw ApiException.proibido("Este registro pertence a outro usuário");
        }
        return r;
    }

    public RegistroAnalise buscarParaEscrita(Long id) {
        Usuario u = logado.atual();
        RegistroAnalise r = registros.findById(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Registro não encontrado"));
        if (!r.getCriadoPor().getId().equals(u.getId())) {
            throw ApiException.proibido("Somente quem criou o registro pode alterá-lo ou executar predições nele");
        }
        return r;
    }

    private void exigirDadosValidos(java.util.Map<String, Object> dados) {
        List<String> erros = validador.validar(dados);
        if (!erros.isEmpty()) {
            throw ApiException.invalido("Os dados violam as regras de qualidade", erros);
        }
    }

    private RegistroResponse paraDto(RegistroAnalise r) {
        return new RegistroResponse(r.getId(), r.getIdentificador(), json.paraMapa(r.getDadosJson()),
                r.getCriadoPor().getId(), r.getCriadoPor().getNome(), r.getCriadoEm(), r.getAtualizadoEm());
    }
}
