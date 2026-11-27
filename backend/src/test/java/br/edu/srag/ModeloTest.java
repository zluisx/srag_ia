package br.edu.srag;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class ModeloTest {
    final Modelo modelo = criar();

    static Modelo criar() {
        try { return new Modelo(new ObjectMapper()); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static Modelo.Entrada ficha(Integer idade, String dispneia, String resto) {
        return new Modelo.Entrada(idade, Map.of("DISPNEIA", dispneia, "DESC_RESP", resto, "SATURACAO", resto,
            "FEBRE", resto, "TOSSE", resto, "FADIGA", resto));
    }

    @Test void avaliaProbabilidadeValidaComNoMaximoTresFatores() {
        var r = modelo.avaliar(ficha(4, "1", "1"));
        assertTrue(r.probabilidade() > 0 && r.probabilidade() < 1);
        assertTrue(r.fatores().size() <= 3);
        assertTrue(java.util.List.of("Baixo", "Médio", "Alto").contains(r.faixa()));
    }

    @Test void rejeitaEntradasInvalidas() {
        assertThrows(ResponseStatusException.class, () -> modelo.validar(null));
        assertThrows(ResponseStatusException.class, () -> modelo.validar(ficha(null, "1", "1")));
        assertThrows(ResponseStatusException.class, () -> modelo.validar(ficha(-1, "1", "1")));
        assertThrows(ResponseStatusException.class, () -> modelo.validar(ficha(99, "1", "1")));
        assertThrows(ResponseStatusException.class, () -> modelo.validar(ficha(4, "3", "1")));
        assertThrows(ResponseStatusException.class, () -> modelo.validar(ficha(4, "9", "9")));
        assertThrows(ResponseStatusException.class, () -> modelo.validar(new Modelo.Entrada(4, Map.of("DISPNEIA", "1"))));
    }
}
