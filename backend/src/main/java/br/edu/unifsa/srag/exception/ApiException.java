package br.edu.unifsa.srag.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Exceção de negócio com o status HTTP que deve ser devolvido. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<String> detalhes;

    public ApiException(HttpStatus status, String mensagem, List<String> detalhes) {
        super(mensagem);
        this.status = status;
        this.detalhes = detalhes == null ? List.of() : detalhes;
    }

    public HttpStatus getStatus() { return status; }
    public List<String> getDetalhes() { return detalhes; }

    public static ApiException naoEncontrado(String msg) { return new ApiException(HttpStatus.NOT_FOUND, msg, null); }
    public static ApiException conflito(String msg) { return new ApiException(HttpStatus.CONFLICT, msg, null); }
    public static ApiException proibido(String msg) { return new ApiException(HttpStatus.FORBIDDEN, msg, null); }
    public static ApiException naoAutenticado(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, msg, null); }
    public static ApiException invalido(String msg, List<String> detalhes) { return new ApiException(HttpStatus.BAD_REQUEST, msg, detalhes); }
    public static ApiException indisponivel(String msg) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, msg, null); }
}
