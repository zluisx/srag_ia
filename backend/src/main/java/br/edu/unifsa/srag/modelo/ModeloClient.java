package br.edu.unifsa.srag.modelo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * PONTO DE ENCAIXE DA IA.
 * O resto do sistema só conhece esta interface. Hoje existem duas implementações:
 *  - ModeloMockClient (modelo.modo=mock): valor de mentira, para desenvolver sem a IA;
 *  - ModeloHttpClient (modelo.modo=http): chama o serviço Python (servico-ia) que carrega o modelo.joblib.
 *
 * Contrato com o serviço Python:
 *   POST /predict  {"dados": {...}}  ->  {"probabilidade": 0.72, "faixa": "alto",
 *                                         "fatores": [{"fator": "...", "efeito": "...", "contribuicao": 0.1}],
 *                                         "versao_modelo": "1.0.0"}
 *   GET  /versao   ->  {"versao_modelo": "1.0.0", ...metadados}
 */
public interface ModeloClient {

    ResultadoModelo prever(Map<String, Object> dados);

    String versao();

    record ResultadoModelo(
            double probabilidade,
            String faixa,
            List<Map<String, Object>> fatores,
            @JsonProperty("versao_modelo") String versaoModelo) {}
}
