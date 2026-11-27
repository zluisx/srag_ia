package br.edu.srag;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:usuarios", "SRAG_OPERADOR_SENHA=apenas-teste-operador",
    "SRAG_GESTOR_SENHA=apenas-teste-gestor", "SRAG_OPERADOR_EMAIL=operador@teste.local",
    "SRAG_GESTOR_EMAIL=gestor@teste.local"})
class UsuariosTest {
    @LocalServerPort int porta;
    final HttpClient cliente = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    JsonNode chamada(String metodo, String path, String body, String token, int esperado) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/" + path))
            .header("Content-Type", "application/json");
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.method(metodo, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var resposta = cliente.send(req.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(esperado, resposta.statusCode(), path + " " + resposta.body());
        return resposta.body().isBlank() ? null : json.readTree(resposta.body());
    }
    String entrar(String email, String senha, int esperado) throws Exception {
        var r = chamada("POST", "login", "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}", null, esperado);
        return esperado == 200 ? r.get("token").asString() : null;
    }
    static String novo(String email, String senha, String perfil) {
        return "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\",\"perfil\":\"" + perfil + "\"}";
    }

    @Test void apenasGestorGerenciaUsuarios() throws Exception {
        String operador = entrar("operador@teste.local", "apenas-teste-operador", 200);
        chamada("GET", "usuarios", null, null, 401);
        chamada("GET", "usuarios", null, operador, 403);
        chamada("POST", "usuarios", novo("x@teste.local", "senha-bem-longa-1", "OPERADOR"), operador, 403);
        chamada("PUT", "usuarios/1/ativo", "{\"ativo\":false}", operador, 403);
    }

    @Test void criaListaERejeitaDadosInvalidos() throws Exception {
        String gestor = entrar("gestor@teste.local", "apenas-teste-gestor", 200);
        var criado = chamada("POST", "usuarios", novo(" Novo@Teste.Local ", "senha-bem-longa-1", "OPERADOR"), gestor, 201);
        assertEquals("novo@teste.local", criado.get("email").asString());
        assertTrue(criado.get("ativo").asBoolean());
        assertFalse(criado.has("senha"), "o hash da senha nunca pode sair pela API");
        chamada("POST", "usuarios", novo("novo@teste.local", "senha-bem-longa-1", "OPERADOR"), gestor, 409);
        chamada("POST", "usuarios", novo("curta@teste.local", "curta", "OPERADOR"), gestor, 400);
        chamada("POST", "usuarios", novo("longa@teste.local", "a".repeat(73), "OPERADOR"), gestor, 400);
        chamada("POST", "usuarios", novo("invalido", "senha-bem-longa-1", "OPERADOR"), gestor, 400);
        chamada("POST", "usuarios", novo("perfil@teste.local", "senha-bem-longa-1", "ADMIN"), gestor, 400);
        var lista = chamada("GET", "usuarios", null, gestor, 200);
        assertTrue(lista.size() >= 3);
        lista.forEach(u -> assertFalse(u.has("senha")));
        entrar("novo@teste.local", "senha-bem-longa-1", 200);
    }

    @Test void desativarBloqueiaLoginETokenJaEmitido() throws Exception {
        String gestor = entrar("gestor@teste.local", "apenas-teste-gestor", 200);
        long id = chamada("POST", "usuarios", novo("saida@teste.local", "senha-bem-longa-1", "OPERADOR"), gestor, 201).get("id").asLong();
        String token = entrar("saida@teste.local", "senha-bem-longa-1", 200);
        chamada("GET", "casos", null, token, 200);
        assertFalse(chamada("PUT", "usuarios/" + id + "/ativo", "{\"ativo\":false}", gestor, 200).get("ativo").asBoolean());
        chamada("GET", "casos", null, token, 401);
        entrar("saida@teste.local", "senha-bem-longa-1", 401);
        assertTrue(chamada("PUT", "usuarios/" + id + "/ativo", "{\"ativo\":true}", gestor, 200).get("ativo").asBoolean());
        entrar("saida@teste.local", "senha-bem-longa-1", 200);
    }

    @Test void mudarPerfilExigeNovoLogin() throws Exception {
        String gestor = entrar("gestor@teste.local", "apenas-teste-gestor", 200);
        long id = chamada("POST", "usuarios", novo("promovido@teste.local", "senha-bem-longa-1", "OPERADOR"), gestor, 201).get("id").asLong();
        String antigo = entrar("promovido@teste.local", "senha-bem-longa-1", 200);
        chamada("GET", "auditoria", null, antigo, 403);
        assertEquals("GESTOR", chamada("PUT", "usuarios/" + id + "/perfil", "{\"perfil\":\"GESTOR\"}", gestor, 200).get("perfil").asString());
        chamada("GET", "auditoria", null, antigo, 401);
        chamada("GET", "auditoria", null, entrar("promovido@teste.local", "senha-bem-longa-1", 200), 200);
    }

    @Test void gestorNaoSeTrancaForaNemAlteraContaInexistente() throws Exception {
        String gestor = entrar("gestor@teste.local", "apenas-teste-gestor", 200);
        long meuId = 0;
        for (var u : chamada("GET", "usuarios", null, gestor, 200))
            if (u.get("email").asString().equals("gestor@teste.local")) meuId = u.get("id").asLong();
        assertTrue(meuId > 0);
        chamada("PUT", "usuarios/" + meuId + "/ativo", "{\"ativo\":false}", gestor, 409);
        chamada("PUT", "usuarios/" + meuId + "/perfil", "{\"perfil\":\"OPERADOR\"}", gestor, 409);
        chamada("PUT", "usuarios/999999/ativo", "{\"ativo\":false}", gestor, 404);
        chamada("PUT", "usuarios/" + meuId + "/ativo", "{}", gestor, 400);
    }
}
