package br.edu.unifsa.srag.domain;

import java.text.Normalizer;

public enum FaixaRisco {
    BAIXO,
    MEDIO,
    ALTO;

    /** Converte o texto devolvido pelo modelo ("baixo", "médio", "alto"). */
    public static FaixaRisco de(String texto) {
        String t = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase();
        return switch (t) {
            case "BAIXO" -> BAIXO;
            case "MEDIO" -> MEDIO;
            case "ALTO" -> ALTO;
            default -> throw new IllegalArgumentException("Faixa de risco desconhecida: " + texto);
        };
    }
}
