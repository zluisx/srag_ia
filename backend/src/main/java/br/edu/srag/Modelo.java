package br.edu.srag;

import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;

@Service
class Modelo {
    record Entrada(@Schema(description="Índice da faixa etária, de 0 a 6", example="4") Integer idade,
                   @Schema(description="Seis sinais. Use 1 para sim, 2 para não ou 9 para ignorado.",
                     example="{\"DISPNEIA\":\"1\",\"DESC_RESP\":\"1\",\"SATURACAO\":\"2\",\"FEBRE\":\"1\",\"TOSSE\":\"1\",\"FADIGA\":\"9\"}")
                   Map<String,String> sinais) {}
    record Fator(String campo, String referencia, double diferenca) {}
    record Resultado(double probabilidade, boolean alerta, String faixa, String versao, List<Fator> fatores) {}
    final JsonNode meta;
    private final JsonNode tabela;
    private final List<String> ordem;
    private final String referencia;

    Modelo(ObjectMapper json) throws IOException {
        try (var metadados = new ClassPathResource("modelo/metadados.json").getInputStream();
             var escores = new ClassPathResource("modelo/escores.json").getInputStream()) {
            meta = json.readTree(metadados);
            tabela = json.readTree(escores);
        }
        ordem = meta.get("ordem").values().stream().map(n -> n.asString()).toList();
        var ref = meta.get("referencia");
        var chave = new StringBuilder(ref.get("idade").asString());
        ordem.forEach(c -> chave.append(ref.get(c).asString()));
        referencia = chave.toString();
    }
    void validar(Entrada e) {
        if (e == null || e.idade() == null || e.idade() < 0 || e.idade() >= meta.get("idades").size())
            throw invalido("Escolha uma faixa etária válida.");
        if (e.sinais() == null || !e.sinais().keySet().equals(Set.copyOf(ordem)))
            throw invalido("Informe exatamente os seis sinais da ficha.");
        if (e.sinais().values().stream().anyMatch(v -> v == null || !List.of("1","2","9").contains(v)))
            throw invalido("Os códigos permitidos para sinais são 1, 2 e 9.");
        if (e.sinais().values().stream().allMatch("9"::equals))
            throw invalido("Informe pelo menos um sinal conhecido.");
    }
    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
    Resultado avaliar(Entrada e) {
        validar(e);
        var chave = new StringBuilder(e.idade().toString());
        ordem.forEach(c -> chave.append(e.sinais().get(c)));
        double p = tabela.get(chave.toString()).asDouble();
        var fatores = new ArrayList<Fator>();
        for (int i=0; i<chave.length(); i++) {
            var alternativa = new StringBuilder(chave);
            alternativa.setCharAt(i, referencia.charAt(i));
            double delta = p - tabela.get(alternativa.toString()).asDouble();
            if (Math.abs(delta) > 1e-12)
                fatores.add(new Fator(i==0 ? "idade" : ordem.get(i-1), ""+referencia.charAt(i), delta));
        }
        fatores.sort(Comparator.comparingDouble((Fator f) -> Math.abs(f.diferenca())).reversed());
        boolean alerta = p >= meta.get("limiar").asDouble();
        String faixa = p >= meta.get("alto").asDouble() ? "Alto" : alerta ? "Médio" : "Baixo";
        return new Resultado(p, alerta, faixa, meta.get("versao").asString(), fatores.stream().limit(3).toList());
    }
}
