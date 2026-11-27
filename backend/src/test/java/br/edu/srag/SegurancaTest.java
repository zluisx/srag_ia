package br.edu.srag;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:seguranca", "SRAG_OPERADOR_SENHA=apenas-teste-operador",
    "SRAG_GESTOR_SENHA=apenas-teste-gestor", "SRAG_OPERADOR_EMAIL=operador@teste.local",
    "SRAG_GESTOR_EMAIL=gestor@teste.local"})
class SegurancaTest {
    @LocalServerPort int porta;
    final HttpClient cliente = HttpClient.newHttpClient();

    HttpResponse<String> post(String path, String body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/" + path))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return cliente.send(req, HttpResponse.BodyHandlers.ofString());
    }
    String login(String email, String senha) { return "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"; }

    @Test void bloqueiaLoginAposVariasFalhasSemAfetarOutrasContas() throws Exception {
        for (int i = 0; i < 5; i++)
            assertEquals(401, post("login", login("gestor@teste.local", "senha-errada-" + i)).statusCode());
        assertEquals(429, post("login", login("gestor@teste.local", "apenas-teste-gestor")).statusCode());
        assertEquals(200, post("login", login("operador@teste.local", "apenas-teste-operador")).statusCode());
    }

    @Test void respostasTemCabecalhosDeSeguranca() throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/")).GET().build();
        var res = cliente.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals("nosniff", res.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("no-referrer", res.headers().firstValue("Referrer-Policy").orElse(""));
    }
}
