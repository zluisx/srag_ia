package br.edu.srag;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api")
@SecurityRequirement(name="jwt")
class Api {
    private final Registros db;
    private final Analises analises;
    private final Modelo modelo;
    Api(Registros db, Analises analises, Modelo modelo) { this.db=db; this.analises=analises; this.modelo=modelo; }
    private long autor(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }

    @GetMapping("/modelo") @Operation(summary="Consultar versão, limiares, esquema e métricas")
    JsonNode modelo() { return modelo.meta; }

    @GetMapping("/casos") @Operation(summary="Listar os próprios registros ativos")
    List<Registros.Caso> casos(@AuthenticationPrincipal Jwt jwt) { return db.casos(autor(jwt)); }

    @PostMapping("/casos") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Criar registro de admissão")
    Registros.Caso criar(@AuthenticationPrincipal Jwt jwt,@RequestBody Modelo.Entrada entrada) {
        return analises.criar(autor(jwt),entrada);
    }
    @GetMapping("/casos/{id}") @Operation(summary="Consultar um registro ativo do solicitante")
    Registros.Caso caso(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { return db.caso(id,autor(jwt),false); }
    @PutMapping("/casos/{id}") @Operation(summary="Atualizar os atributos da admissão")
    Registros.Caso editar(@PathVariable long id,@AuthenticationPrincipal Jwt jwt,@RequestBody Modelo.Entrada entrada) {
        return analises.editar(id,autor(jwt),entrada);
    }
    @DeleteMapping("/casos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Arquivar registro e preservar histórico")
    void excluir(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { db.excluir(id,autor(jwt)); }

    @PostMapping("/casos/{id}/predicoes") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Executar modelo e gravar nova predição")
    Registros.Predicao prever(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { return analises.executar(id,autor(jwt)); }

    @GetMapping("/predicoes") @Operation(summary="Consultar histórico próprio, opcionalmente por caso")
    List<Registros.Predicao> historico(@RequestParam(required=false) Long caso,@AuthenticationPrincipal Jwt jwt) {
        return db.historico(autor(jwt),caso,null,null,null,null);
    }
    @PutMapping("/predicoes/{id}/revisao") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Solicitar revisão humana")
    void revisao(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { db.revisar(id,autor(jwt)); }

    @GetMapping("/auditoria") @Operation(summary="Consultar histórico geral e pedidos de revisão como gestor")
    List<Registros.Predicao> auditoria(@RequestParam(required=false) String email,@RequestParam(required=false) String faixa,
        @RequestParam(required=false) LocalDate inicio,@RequestParam(required=false) LocalDate fim) {
        if (inicio!=null && fim!=null && inicio.isAfter(fim))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A data final deve ser igual ou posterior à inicial.");
        if (faixa!=null && !List.of("Baixo","Médio","Alto").contains(faixa))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Faixa de risco inválida.");
        return db.historico(null,null,email,faixa,inicio,fim);
    }
}
