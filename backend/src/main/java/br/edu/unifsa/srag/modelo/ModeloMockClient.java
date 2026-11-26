package br.edu.unifsa.srag.modelo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IA "de mentira" para o front-end e o back-end funcionarem antes de o modelo real chegar.
 * NÃO é o modelo treinado: só soma pontos para alguns sintomas.
 */
@Component
@ConditionalOnProperty(name = "modelo.modo", havingValue = "mock", matchIfMissing = true)
public class ModeloMockClient implements ModeloClient {

    @Override
    public ResultadoModelo prever(Map<String, Object> dados) {
        double p = 0.20;
        List<Map<String, Object>> fatores = new ArrayList<>();
        p += somar(dados, "SATURACAO", 0.25, "Saturação de O2 < 95%", fatores);
        p += somar(dados, "DISPNEIA", 0.15, "Dispneia", fatores);
        p += somar(dados, "DESC_RESP", 0.15, "Desconforto respiratório", fatores);
        p += somar(dados, "CARDIOPATI", 0.10, "Doença cardiovascular crônica", fatores);
        p = Math.min(0.95, p);
        String faixa = p < 0.40 ? "baixo" : (p < 0.70 ? "médio" : "alto");
        return new ResultadoModelo(p, faixa, fatores, versao());
    }

    @Override
    public String versao() {
        return "mock-0.0.0";
    }

    private double somar(Map<String, Object> dados, String campo, double peso, String rotulo,
                         List<Map<String, Object>> fatores) {
        Object v = dados.get(campo);
        if (v instanceof Number n && n.intValue() == 1) {
            fatores.add(Map.of("fator", rotulo, "efeito", "aumenta o risco", "contribuicao", peso));
            return peso;
        }
        return 0.0;
    }
}
