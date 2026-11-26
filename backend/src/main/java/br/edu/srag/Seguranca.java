package br.edu.srag;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;

@Configuration
@SecurityScheme(name="jwt", type=SecuritySchemeType.HTTP, scheme="bearer", bearerFormat="JWT")
public class Seguranca {
    @Bean SecretKey chave(@Value("${SRAG_JWT_SECRET:}") String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
        }
        if (bytes.length < 32) throw new IllegalArgumentException("SRAG_JWT_SECRET exige ao menos 32 bytes.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean BCryptPasswordEncoder senhas() { return new BCryptPasswordEncoder(12); }

    /** Recusa o token se a conta foi desativada ou mudou de perfil depois do login. */
    @Bean JwtDecoder decoder(SecretKey chave, Usuarios usuarios) {
        var decoder = NimbusJwtDecoder.withSecretKey(chave).build();
        OAuth2TokenValidator<Jwt> contaVigente = jwt -> {
            try {
                var conta = usuarios.buscarPorId(Long.parseLong(jwt.getSubject()));
                if (conta.isPresent() && conta.get().ativo() && conta.get().perfil().equals(jwt.getClaimAsString("scope")))
                    return OAuth2TokenValidatorResult.success();
            } catch (NumberFormatException ignorado) { }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Conta inativa ou perfil alterado.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("regulacao-srag"), contaVigente));
        return decoder;
    }

    @Bean SecurityFilterChain acesso(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable())
            .headers(h -> h.referrerPolicy(r -> r.policy(
                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/login").permitAll()
                .requestMatchers("/api/auditoria/**", "/api/auditoria", "/api/usuarios/**", "/api/usuarios").hasAuthority("SCOPE_GESTOR")
                .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .oauth2ResourceServer(r -> r.jwt(Customizer.withDefaults())
                .authenticationEntryPoint((req,res,erro) -> {
                    res.setHeader("WWW-Authenticate","Bearer");
                    problema(res,401,"Autenticação necessária ou sessão expirada.");
                })
                .accessDeniedHandler((req,res,erro) -> problema(res,403,"O perfil não tem acesso a este recurso."))).build();
    }
    private static void problema(jakarta.servlet.http.HttpServletResponse res, int status, String detalhe)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/problem+json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":"+status+",\"detail\":\""+detalhe+"\"}");
    }
}
