package br.edu.unifsa.srag.service;

import br.edu.unifsa.srag.domain.Usuario;
import br.edu.unifsa.srag.dto.Dtos.LoginRequest;
import br.edu.unifsa.srag.dto.Dtos.TokenResponse;
import br.edu.unifsa.srag.exception.ApiException;
import br.edu.unifsa.srag.repository.UsuarioRepository;
import br.edu.unifsa.srag.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public TokenResponse login(LoginRequest req) {
        // Mesma mensagem para e-mail inexistente e senha errada (não revela quais e-mails existem)
        Usuario u = usuarios.findByEmail(req.email())
                .filter(x -> encoder.matches(req.senha(), x.getSenhaHash()))
                .orElseThrow(() -> ApiException.naoAutenticado("E-mail ou senha inválidos"));
        if (!u.isAtivo()) {
            throw ApiException.naoAutenticado("Usuário desativado");
        }
        String token = jwt.gerar(u);
        return new TokenResponse(token, "Bearer", jwt.expiracao(token), u.getNome(), u.getPerfil());
    }
}
