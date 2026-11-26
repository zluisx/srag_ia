package br.edu.unifsa.srag.modelo;

import br.edu.unifsa.srag.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** Chama o microserviço Python que carrega o modelo.joblib (modelo.modo=http). */
@Component
@ConditionalOnProperty(name = "modelo.modo", havingValue = "http")
public class ModeloHttpClient implements ModeloClient {

    private final RestClient http;

    public ModeloHttpClient(@Value("${modelo.url}") String url) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3000);
        fabrica.setReadTimeout(15000);
        this.http = RestClient.builder().requestFactory(fabrica).baseUrl(url).build();
    }

    @Override
    public ResultadoModelo prever(Map<String, Object> dados) {
        try {
            ResultadoModelo r = http.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("dados", dados))
                    .retrieve()
                    .body(ResultadoModelo.class);
            if (r == null || r.probabilidade() < 0 || r.probabilidade() > 1 || r.faixa() == null) {
                throw ApiException.indisponivel("Resposta inválida do serviço de IA");
            }
            return r;
        } catch (RestClientException e) {
            throw ApiException.indisponivel("Serviço de IA indisponível no momento");
        }
    }

    @Override
    public String versao() {
        try {
            Map<String, Object> meta = http.get().uri("/versao").retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return meta == null ? "desconhecida" : String.valueOf(meta.get("versao_modelo"));
        } catch (RestClientException e) {
            throw ApiException.indisponivel("Serviço de IA indisponível no momento");
        }
    }
}
