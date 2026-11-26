package br.edu.unifsa.srag.service;

import br.edu.unifsa.srag.domain.Usuario;
import br.edu.unifsa.srag.dto.Dtos.UsuarioRequest;
import br.edu.unifsa.srag.dto.Dtos.UsuarioResponse;
import br.edu.unifsa.srag.exception.ApiException;
import br.edu.unifsa.srag.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest req) {
        String email = req.email().trim().toLowerCase();
        if (usuarios.existsByEmail(email)) {
            throw ApiException.conflito("Já existe um usuário com este e-mail");
        }
        Usuario u = new Usuario();
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setSenhaHash(encoder.encode(req.senha()));   // BCrypt
        u.setPerfil(req.perfil());
        return paraDto(usuarios.save(u));
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream().map(this::paraDto).toList();
    }

    @Transactional
    public UsuarioResponse alterarAtivo(Long id, boolean ativo) {
        Usuario u = usuarios.findById(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Usuário não encontrado"));
        u.setAtivo(ativo);
        return paraDto(u);
    }

    private UsuarioResponse paraDto(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo(), u.getCriadoEm());
    }
}
