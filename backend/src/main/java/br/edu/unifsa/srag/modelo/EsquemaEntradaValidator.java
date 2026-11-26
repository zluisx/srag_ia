package br.edu.unifsa.srag.modelo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Regras de qualidade na entrada da API (Governança, item 4): valida os dados do caso
 * contra o esquema_entrada.json gerado pelo notebook do modelo (campos, tipos e valores válidos).
 */
@Component
public class EsquemaEntradaValidator {

    private final JsonNode esquema;

    public EsquemaEntradaValidator(ResourceLoader loader, ObjectMapper mapper,
                                   @Value("${modelo.esquema-path}") String caminho) throws IOException {
        Resource recurso = loader.getResource(caminho);
        try (InputStream in = recurso.getInputStream()) {
            this.esquema = mapper.readTree(in);
        }
    }

    /** O esquema completo (o front usa para montar o formulário). */
    public JsonNode esquema() {
        return esquema;
    }

    /** Devolve a lista de erros; lista vazia = dados válidos. */
    public List<String> validar(Map<String, Object> dados) {
        List<String> erros = new ArrayList<>();

        dados.forEach((campo, valor) -> {
            JsonNode regra = esquema.get(campo);
            if (regra == null) {
                erros.add(campo + ": campo desconhecido");
                return;
            }
            if (valor == null) {
                return;   // vazio é aceito; o pipeline do modelo imputa os faltantes
            }
            String tipo = regra.path("tipo").asText();
            switch (tipo) {
                case "inteiro" -> validarInteiro(campo, valor, regra, erros);
                case "binario", "categorico" -> validarLista(campo, valor, regra, erros);
                default -> erros.add(campo + ": tipo de regra desconhecido no esquema");
            }
        });

        esquema.fieldNames().forEachRemaining(campo -> {
            if (esquema.get(campo).path("obrigatorio").asBoolean(false) && dados.get(campo) == null) {
                erros.add(campo + ": campo obrigatório");
            }
        });
        return erros;
    }

    private void validarInteiro(String campo, Object valor, JsonNode regra, List<String> erros) {
        if (!(valor instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            erros.add(campo + ": deve ser um número inteiro");
            return;
        }
        double v = n.doubleValue();
        if (regra.has("minimo") && v < regra.get("minimo").asDouble()) {
            erros.add(campo + ": mínimo permitido é " + regra.get("minimo").asText());
        }
        if (regra.has("maximo") && v > regra.get("maximo").asDouble()) {
            erros.add(campo + ": máximo permitido é " + regra.get("maximo").asText());
        }
    }

    private void validarLista(String campo, Object valor, JsonNode regra, List<String> erros) {
        for (JsonNode valido : regra.path("valores_validos")) {
            if (valido.isNumber() && valor instanceof Number n) {
                if (valido.asDouble() == n.doubleValue()) return;
            } else if (valido.asText().equals(String.valueOf(valor))) {
                return;
            }
        }
        List<String> aceitos = new ArrayList<>();
        regra.path("valores_validos").forEach(v -> aceitos.add(v.asText()));
        erros.add(campo + ": valor inválido '" + valor + "'. Valores aceitos: " + aceitos);
    }
}
