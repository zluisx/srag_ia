package br.edu.srag;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import javax.crypto.SecretKey;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api")
class Sessao {
    record Login(@NotBlank @Email String email, @NotBlank @Size(max=72) String senha) {}
    record Acesso(String token, String perfil, String email) {}
    private final Usuarios usuarios;
    private final BCryptPasswordEncoder bcrypt;
    private final SecretKey chave;
    private final String hashFicticio;
    private static final int MAX_FALHAS = 5;
    private static final long BLOQUEIO_MS = 5 * 60_000L;
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> falhas = new java.util.concurrent.ConcurrentHashMap<>();

    Sessao(Usuarios usuarios, BCryptPasswordEncoder bcrypt, SecretKey chave) {
        this.usuarios = usuarios;
        this.bcrypt = bcrypt;
        this.chave = chave;
        this.hashFicticio = bcrypt.encode(java.util.UUID.randomUUID().toString());
    }
    @Bean ApplicationRunner provisionar(Environment env) {
        return args -> {
            for (String perfil : new String[]{"OPERADOR", "GESTOR"}) {
                String email = env.getProperty("SRAG_" + perfil + "_EMAIL", perfil.toLowerCase(Locale.ROOT) + "@demo.local")
                                  .trim().toLowerCase(Locale.ROOT);
                String senha = env.getProperty("SRAG_" + perfil + "_SENHA", "");
                if (usuarios.buscar(email).isPresent() || senha.isBlank()) continue;
                if (senha.length() < 12 || senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                    throw new IllegalArgumentException("A senha inicial deve ter pelo menos 12 caracteres e até 72 bytes.");
                usuarios.adicionar(email,bcrypt.encode(senha),perfil);
            }
        };
    }

    @PostMapping("/login")
    @Operation(summary="Entrar por e-mail e senha e obter JWT válido por 30 minutos")
    Acesso login(@Valid @RequestBody Login login) throws JOSEException {
        String email = login.email().trim().toLowerCase(Locale.ROOT);
        var estado = falhas.get(email);
        if (estado != null && estado[0] >= MAX_FALHAS && System.currentTimeMillis() - estado[1] < BLOQUEIO_MS)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde alguns minutos.");
        var encontrado = usuarios.buscar(email);
        boolean valido = login.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
            && bcrypt.matches(login.senha(), encontrado.map(Usuarios.Usuario::senha).orElse(hashFicticio));
        if (!valido || encontrado.isEmpty()) {
            falhas.merge(email, new long[]{1, System.currentTimeMillis()}, (a, b) ->
                new long[]{b[1] - a[1] >= BLOQUEIO_MS ? 1 : a[0] + 1, b[1]});
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }
        falhas.remove(email);
        var u = encontrado.get();
        var agora = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("regulacao-srag").subject(Long.toString(u.id()))
            .claim("scope",u.perfil()).issueTime(Date.from(agora))
            .expirationTime(Date.from(agora.plusSeconds(1800))).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);
        jwt.sign(new MACSigner(chave));
        return new Acesso(jwt.serialize(),u.perfil(),u.email());
    }
}
