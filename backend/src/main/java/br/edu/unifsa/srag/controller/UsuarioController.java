package br.edu.unifsa.srag.controller;

import br.edu.unifsa.srag.dto.Dtos.UsuarioRequest;
import br.edu.unifsa.srag.dto.Dtos.UsuarioResponse;
import br.edu.unifsa.srag.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('GESTOR')")
@Tag(name = "Usuários (somente gestor)")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarios;

    public UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Operation(summary = "Cria um usuário (operador ou gestor)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@Valid @RequestBody UsuarioRequest req) {
        return usuarios.criar(req);
    }

    @Operation(summary = "Lista os usuários")
    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarios.listar();
    }

    @Operation(summary = "Ativa ou desativa um usuário")
    @PatchMapping("/{id}/ativo")
    public UsuarioResponse alterarAtivo(@PathVariable Long id, @RequestParam boolean valor) {
        return usuarios.alterarAtivo(id, valor);
    }
}
