package br.edu.srag;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/** Gestão de usuários. O acesso é restrito ao perfil GESTOR em {@link Seguranca}. */
@RestController
@RequestMapping("/api/usuarios")
@SecurityRequirement(name="jwt")
class UsuariosApi {
    /** Visão pública de uma conta: nunca inclui o hash da senha. */
    record UsuarioPublico(long id, String email, String perfil, boolean ativo) {
        static UsuarioPublico de(Usuarios.Usuario u) { return new UsuarioPublico(u.id(), u.email(), u.perfil(), u.ativo()); }
    }
    record NovoUsuario(@NotBlank @Email @Size(max=255) String email,
                       @NotBlank @Size(min=12, message="A senha deve ter pelo menos 12 caracteres.") String senha,
                       @NotBlank @Pattern(regexp="OPERADOR|GESTOR", message="O perfil deve ser OPERADOR ou GESTOR.") String perfil) {}
    record NovoPerfil(@NotBlank @Pattern(regexp="OPERADOR|GESTOR", message="O perfil deve ser OPERADOR ou GESTOR.") String perfil) {}
    record NovoEstado(@NotNull Boolean ativo) {}

    private final Usuarios usuarios;
    private final BCryptPasswordEncoder bcrypt;
    UsuariosApi(Usuarios usuarios, BCryptPasswordEncoder bcrypt) { this.usuarios = usuarios; this.bcrypt = bcrypt; }

    @GetMapping @Operation(summary="Listar usuários (gestor)")
    List<UsuarioPublico> listar() { return usuarios.listar().stream().map(UsuarioPublico::de).toList(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Criar usuário com perfil OPERADOR ou GESTOR (gestor)")
    UsuarioPublico criar(@Valid @RequestBody NovoUsuario novo) {
        if (novo.senha().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter no máximo 72 bytes.");
        String email = novo.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.buscar(email).isPresent()) throw jaExiste();
        try {
            return UsuarioPublico.de(usuarios.criar(email, bcrypt.encode(novo.senha()), novo.perfil()));
        } catch (DuplicateKeyException e) {
            throw jaExiste();
        }
    }

    @PutMapping("/{id}/perfil") @Operation(summary="Alterar o perfil de um usuário (gestor). Vale no próximo acesso à API.")
    UsuarioPublico perfil(@PathVariable long id, @Valid @RequestBody NovoPerfil corpo, @AuthenticationPrincipal Jwt jwt) {
        protegerPropriaConta(id, jwt, "Você não pode alterar o próprio perfil.");
        conferir(usuarios.alterarPerfil(id, corpo.perfil()));
        return UsuarioPublico.de(usuarios.buscarPorId(id).orElseThrow());
    }

    @PutMapping("/{id}/ativo") @Operation(summary="Ativar ou desativar um usuário (gestor). A desativação derruba o token vigente.")
    UsuarioPublico ativo(@PathVariable long id, @Valid @RequestBody NovoEstado corpo, @AuthenticationPrincipal Jwt jwt) {
        if (!corpo.ativo()) protegerPropriaConta(id, jwt, "Você não pode desativar a própria conta.");
        conferir(usuarios.alterarAtivo(id, corpo.ativo()));
        return UsuarioPublico.de(usuarios.buscarPorId(id).orElseThrow());
    }

    /** Impede que o gestor se tranque fora do sistema (e que o sistema fique sem gestor). */
    private static void protegerPropriaConta(long id, Jwt jwt, String mensagem) {
        if (Long.parseLong(jwt.getSubject()) == id) throw new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
    }
    private static void conferir(int alterados) {
        if (alterados == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
    }
    private static ResponseStatusException jaExiste() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail.");
    }
}
