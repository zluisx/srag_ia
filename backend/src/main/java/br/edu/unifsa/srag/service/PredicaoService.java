package br.edu.unifsa.srag.service;

import br.edu.unifsa.srag.domain.FaixaRisco;
import br.edu.unifsa.srag.domain.Perfil;
import br.edu.unifsa.srag.domain.Predicao;
import br.edu.unifsa.srag.domain.RegistroAnalise;
import br.edu.unifsa.srag.domain.Usuario;
import br.edu.unifsa.srag.dto.Dtos.PageResponse;
import br.edu.unifsa.srag.dto.Dtos.PredicaoResponse;
import br.edu.unifsa.srag.exception.ApiException;
import br.edu.unifsa.srag.modelo.EsquemaEntradaValidator;
import br.edu.unifsa.srag.modelo.ModeloClient;
import br.edu.unifsa.srag.modelo.ModeloClient.ResultadoModelo;
import br.edu.unifsa.srag.repository.PredicaoRepository;
import br.edu.unifsa.srag.security.UsuarioLogado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class PredicaoService {

    private final PredicaoRepository predicoes;
    private final RegistroService registroService;
    private final ModeloClient modelo;
    private final EsquemaEntradaValidator validador;
    private final UsuarioLogado logado;
    private final JsonUtil json;

    public PredicaoService(PredicaoRepository predicoes, RegistroService registroService, ModeloClient modelo,
                           EsquemaEntradaValidator validador, UsuarioLogado logado, JsonUtil json) {
        this.predicoes = predicoes;
        this.registroService = registroService;
        this.modelo = modelo;
        this.validador = validador;
        this.logado = logado;
        this.json = json;
    }

    /** Executa o modelo para um registro e grava uma NOVA linha no histórico. */
    @Transactional
    public PredicaoResponse executar(Long registroId) {
        Usuario u = logado.atual();
        RegistroAnalise registro = registroService.buscarParaEscrita(registroId);

        Map<String, Object> dados = json.paraMapa(registro.getDadosJson());
        List<String> erros = validador.validar(dados);   // qualidade também na hora da predição
        if (!erros.isEmpty()) {
            throw ApiException.invalido("Os dados do registro violam as regras de qualidade", erros);
        }

        ResultadoModelo r = modelo.prever(dados);        // <<< ponto de encaixe da IA

        Predicao p = new Predicao();
        p.setRegistro(registro);
        p.setUsuario(u);
        p.setEntradaJson(registro.getDadosJson());
        p.setProbabilidade(r.probabilidade());
        p.setFaixa(FaixaRisco.de(r.faixa()));
        p.setFatoresJson(json.paraJson(r.fatores() == null ? List.of() : r.fatores()));
        p.setVersaoModelo(r.versaoModelo());
        return paraDto(predicoes.save(p));
    }

    @Transactional(readOnly = true)
    public List<PredicaoResponse> historicoDoRegistro(Long registroId) {
        registroService.buscarParaLeitura(registroId);   // checa o acesso
        return predicoes.findByRegistroIdOrderByCriadoEmDesc(registroId).stream().map(this::paraDto).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PredicaoResponse> minhas(Pageable pageable) {
        Usuario u = logado.atual();
        return PageResponse.de(predicoes.findByUsuarioId(u.getId(), pageable).map(this::paraDto));
    }

    @Transactional(readOnly = true)
    public PredicaoResponse obter(Long id) {
        return paraDto(buscarComAcesso(id));
    }

    /** Auditoria (GESTOR): histórico geral com filtros por período, usuário e faixa de risco. */
    @Transactional(readOnly = true)
    public PageResponse<PredicaoResponse> auditoria(Instant inicio, Instant fim, Long usuarioId,
                                                    FaixaRisco faixa, Boolean somenteRevisao, Pageable pageable) {
        Specification<Predicao> spec = (root, q, cb) -> cb.conjunction();
        if (inicio != null) spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("criadoEm"), inicio));
        if (fim != null) spec = spec.and((root, q, cb) -> cb.lessThan(root.get("criadoEm"), fim));
        if (usuarioId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("usuario").get("id"), usuarioId));
        if (faixa != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("faixa"), faixa));
        if (Boolean.TRUE.equals(somenteRevisao)) spec = spec.and((root, q, cb) -> cb.isTrue(root.get("revisaoSolicitada")));
        Page<Predicao> pagina = predicoes.findAll(spec, pageable);
        return PageResponse.de(pagina.map(this::paraDto));
    }

    /** Botão "Solicitar revisão humana" (LGPD, art. 20). */
    @Transactional
    public PredicaoResponse solicitarRevisao(Long id, String motivo) {
        Predicao p = buscarComAcesso(id);
        if (p.isRevisaoSolicitada()) {
            throw ApiException.conflito("A revisão humana já foi solicitada para esta predição");
        }
        p.setRevisaoSolicitada(true);
        p.setRevisaoMotivo(motivo.trim());
        p.setRevisaoEm(Instant.now());
        return paraDto(p);
    }

    public String versaoDoModelo() {
        return modelo.versao();
    }

    private Predicao buscarComAcesso(Long id) {
        Usuario u = logado.atual();
        Predicao p = predicoes.findById(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Predição não encontrada"));
        if (u.getPerfil() != Perfil.GESTOR && !p.getUsuario().getId().equals(u.getId())) {
            throw ApiException.proibido("Esta predição pertence a outro usuário");
        }
        return p;
    }

    private PredicaoResponse paraDto(Predicao p) {
        return new PredicaoResponse(p.getId(), p.getRegistro().getId(), p.getUsuario().getId(), p.getUsuario().getNome(),
                p.getProbabilidade(), p.getFaixa(), json.paraListaDeMapas(p.getFatoresJson()), p.getVersaoModelo(),
                json.paraMapa(p.getEntradaJson()), p.getCriadoEm(),
                p.isRevisaoSolicitada(), p.getRevisaoMotivo(), p.getRevisaoEm());
    }
}
