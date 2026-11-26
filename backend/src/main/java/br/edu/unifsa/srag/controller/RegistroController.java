package br.edu.unifsa.srag.controller;

import br.edu.unifsa.srag.dto.Dtos.PageResponse;
import br.edu.unifsa.srag.dto.Dtos.PredicaoResponse;
import br.edu.unifsa.srag.dto.Dtos.RegistroRequest;
import br.edu.unifsa.srag.dto.Dtos.RegistroResponse;
import br.edu.unifsa.srag.service.PredicaoService;
import br.edu.unifsa.srag.service.RegistroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registros")
@Tag(name = "Registros de análise")
@SecurityRequirement(name = "bearerAuth")
public class RegistroController {

    private final RegistroService registros;
    private final PredicaoService predicoes;

    public RegistroController(RegistroService registros, PredicaoService predicoes) {
        this.registros = registros;
        this.predicoes = predicoes;
    }

    @Operation(summary = "Cria um registro (dados validados pelo esquema do modelo)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse criar(@Valid @RequestBody RegistroRequest req) {
        return registros.criar(req);
    }

    @Operation(summary = "Lista registros (operador: os seus; gestor: todos). Paginado")
    @GetMapping
    public PageResponse<RegistroResponse> listar(
            @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return registros.listar(pageable);
    }

    @Operation(summary = "Consulta um registro")
    @GetMapping("/{id}")
    public RegistroResponse obter(@PathVariable Long id) {
        return registros.obter(id);
    }

    @Operation(summary = "Atualiza um registro (somente o dono)")
    @PutMapping("/{id}")
    public RegistroResponse atualizar(@PathVariable Long id, @Valid @RequestBody RegistroRequest req) {
        return registros.atualizar(id, req);
    }

    @Operation(summary = "Exclui um registro sem predições (somente o dono)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        registros.excluir(id);
    }

    @Operation(summary = "Executa o modelo de IA para o registro e grava no histórico")
    @PostMapping("/{id}/predicoes")
    @ResponseStatus(HttpStatus.CREATED)
    public PredicaoResponse prever(@PathVariable Long id) {
        return predicoes.executar(id);
    }

    @Operation(summary = "Histórico de predições de um registro")
    @GetMapping("/{id}/predicoes")
    public List<PredicaoResponse> historico(@PathVariable Long id) {
        return predicoes.historicoDoRegistro(id);
    }
}
