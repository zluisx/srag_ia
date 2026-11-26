package br.edu.unifsa.srag.dto;

import br.edu.unifsa.srag.domain.FaixaRisco;
import br.edu.unifsa.srag.domain.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Todos os DTOs (entrada e saída da API) num só arquivo, para facilitar a leitura. */
public final class Dtos {

    private Dtos() {}

    // ---------- Autenticação ----------
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String senha) {}

    public record TokenResponse(String token, String tipo, Instant expiraEm, String nome, Perfil perfil) {}

    // ---------- Usuários ----------
    public record UsuarioRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72) String senha,
            @NotNull Perfil perfil) {}

    public record UsuarioResponse(Long id, String nome, String email, Perfil perfil, boolean ativo, Instant criadoEm) {}

    // ---------- Registros ----------
    public record RegistroRequest(
            @NotBlank @Size(max = 60) String identificador,
            @NotNull Map<String, Object> dados) {}

    public record RegistroResponse(
            Long id, String identificador, Map<String, Object> dados,
            Long criadoPorId, String criadoPorNome, Instant criadoEm, Instant atualizadoEm) {}

    // ---------- Predições ----------
    public record PredicaoResponse(
            Long id, Long registroId, Long usuarioId, String usuarioNome,
            double probabilidade, FaixaRisco faixa, List<Map<String, Object>> fatores,
            String versaoModelo, Map<String, Object> entrada, Instant criadoEm,
            boolean revisaoSolicitada, String revisaoMotivo, Instant revisaoEm) {}

    public record RevisaoRequest(@NotBlank @Size(max = 500) String motivo) {}

    // ---------- Paginação e erros ----------
    public record PageResponse<T>(List<T> conteudo, int pagina, int tamanho, long total, int totalPaginas) {
        public static <T> PageResponse<T> de(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record ApiError(Instant timestamp, int status, String erro, String mensagem,
                           List<String> detalhes, String caminho) {}
}
