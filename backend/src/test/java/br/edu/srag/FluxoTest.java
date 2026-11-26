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
    "spring.datasource.url=jdbc:h2:mem:fluxo", "SRAG_OPERADOR_SENHA=apenas-teste-operador",
    "SRAG_GESTOR_SENHA=apenas-teste-gestor", "SRAG_OPERADOR_EMAIL=operador@teste.local",
    "SRAG_GESTOR_EMAIL=gestor@teste.local"})
class FluxoTest {
    @LocalServerPort int porta;
    final HttpClient cliente = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();
    static final String FICHA = """
        {"idade":4,"sinais":{"DISPNEIA":"1","DESC_RESP":"1","SATURACAO":"2","FEBRE":"1","TOSSE":"1","FADIGA":"9"}}
        """;

    JsonNode chamada(String metodo,String path,String body,String token,int esperado) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+porta+"/api/"+path))
            .header("Content-Type","application/json");
        if(token!=null) req.header("Authorization","Bearer "+token);
        req.method(metodo,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var resposta=cliente.send(req.build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(esperado,resposta.statusCode(),path+" "+resposta.body());
        return resposta.body().isBlank()?null:json.readTree(resposta.body());
    }
    String entrar(String perfil) throws Exception {
        return chamada("POST","login","{\"email\":\""+perfil+"@teste.local\",\"senha\":\"apenas-teste-"+perfil+"\"}",null,200)
            .get("token").asString();
    }
    @Test void fluxoComHistoricoIsolamentoERevisao() throws Exception {
        chamada("GET","modelo",null,null,401);
        String operador=entrar("operador"), gestor=entrar("gestor");
        chamada("GET","auditoria",null,operador,403);
        chamada("POST","casos",FICHA.replace("\"idade\":4","\"idade\":8"),operador,400);
        chamada("POST","casos",FICHA.replace("\"1\"","\"9\"").replace("\"2\"","\"9\""),operador,400);
        long caso=chamada("POST","casos",FICHA,operador,201).get("id").asLong();
        String caminho="casos/"+caso;
        chamada("GET",caminho,null,gestor,404);
        var p=chamada("POST",caminho+"/predicoes",null,operador,201);
        assertFalse(p.get("resultado").get("versao").asString().isBlank());
        String revisao="predicoes/"+p.get("id").asLong()+"/revisao";
        chamada("PUT",revisao,null,gestor,404);
        chamada("PUT",revisao,null,operador,204);
        chamada("PUT",caminho,FICHA.replace("\"idade\":4","\"idade\":1"),operador,200);
        chamada("POST",caminho+"/predicoes",null,operador,201);
        chamada("DELETE",caminho,null,operador,204);
        chamada("GET",caminho,null,operador,404);
        var historico=chamada("GET","predicoes?caso="+caso,null,operador,200);
        assertEquals(2,historico.size());
        assertEquals(4,historico.get(1).get("entrada").get("idade").asInt());
        assertTrue(historico.get(1).get("revisao").asBoolean());
        assertEquals(2,chamada("GET","auditoria?email=operador@teste.local",null,gestor,200).size());
        assertEquals(0,chamada("GET","auditoria?fim=2020-01-01",null,gestor,200).size());
    }
}
