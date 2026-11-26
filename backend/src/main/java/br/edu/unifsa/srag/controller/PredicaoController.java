package br.edu.unifsa.srag.controller;

import br.edu.unifsa.srag.domain.FaixaRisco;
import br.edu.unifsa.srag.dto.Dtos.PageResponse;
import br.edu.unifsa.srag.dto.Dtos.PredicaoResponse;
import br.edu.unifsa.srag.dto.Dtos.RevisaoRequest;
import br.edu.unifsa.srag.service.PredicaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/predicoes")
@Tag(name = "Predições e auditoria")
@SecurityRequirement(name = "bearerAuth")
public class PredicaoController {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final PredicaoService predicoes;

    public PredicaoController(PredicaoService predicoes) {
        this.predicoes = predicoes;
    }

    @Operation(summary = "Minhas predições (paginado)")
    @GetMapping("/minhas")
    public PageResponse<PredicaoResponse> minhas(
            @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return predicoes.minhas(pageable);
    }

    @Operation(summary = "Auditoria: histórico geral com filtros por período (datas AAAA-MM-DD), usuário e faixa (somente gestor)")
    @GetMapping
    @PreAuthorize("hasRole('GESTOR')")
    public PageResponse<PredicaoResponse> auditoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) FaixaRisco faixa,
            @RequestParam(required = false) Boolean somenteRevisao,
            @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        var de = inicio == null ? null : inicio.atStartOfDay(FUSO).toInstant();
        var ate = fim == null ? null : fim.plusDays(1).atStartOfDay(FUSO).toInstant();   // fim inclusivo
        return predicoes.auditoria(de, ate, usuarioId, faixa, somenteRevisao, pageable);
    }

    @Operation(summary = "Consulta uma predição")
    @GetMapping("/{id}")
    public PredicaoResponse obter(@PathVariable Long id) {
        return predicoes.obter(id);
    }

    @Operation(summary = "Solicitar revisão humana de uma predição")
    @PostMapping("/{id}/revisao")
    public PredicaoResponse solicitarRevisao(@PathVariable Long id, @Valid @RequestBody RevisaoRequest req) {
        return predicoes.solicitarRevisao(id, req.motivo());
    }
}
