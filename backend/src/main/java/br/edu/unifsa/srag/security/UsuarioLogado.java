package br.edu.unifsa.srag.security;

import br.edu.unifsa.srag.domain.Usuario;
import br.edu.unifsa.srag.exception.ApiException;
import br.edu.unifsa.srag.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Devolve o usuário autenticado na requisição atual. */
@Component
public class UsuarioLogado {

    private final UsuarioRepository usuarios;

    public UsuarioLogado(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Usuario atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw ApiException.naoAutenticado("Autenticação necessária");
        }
        return usuarios.findByEmail(auth.getName())
                .orElseThrow(() -> ApiException.naoAutenticado("Usuário não encontrado"));
    }
}
