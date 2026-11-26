package br.edu.unifsa.srag.controller;

import br.edu.unifsa.srag.modelo.EsquemaEntradaValidator;
import br.edu.unifsa.srag.service.PredicaoService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/modelo")
@Tag(name = "Modelo de IA")
@SecurityRequirement(name = "bearerAuth")
public class ModeloController {

    private final PredicaoService predicoes;
    private final EsquemaEntradaValidator validador;

    public ModeloController(PredicaoService predicoes, EsquemaEntradaValidator validador) {
        this.predicoes = predicoes;
        this.validador = validador;
    }

    @Operation(summary = "Versão do modelo em uso")
    @GetMapping("/versao")
    public Map<String, String> versao() {
        return Map.of("versao_modelo", predicoes.versaoDoModelo());
    }

    @Operation(summary = "Esquema de entrada (campos, tipos e valores válidos) para o front montar o formulário")
    @GetMapping("/esquema")
    public JsonNode esquema() {
        return validador.esquema();
    }
}
