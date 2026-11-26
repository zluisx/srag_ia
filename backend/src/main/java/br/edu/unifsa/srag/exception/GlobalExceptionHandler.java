package br.edu.unifsa.srag.exception;

import br.edu.unifsa.srag.dto.Dtos.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/** Respostas de erro padronizadas (ApiError) para toda a API. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException e, HttpServletRequest req) {
        return resp(e.getStatus(), e.getMessage(), e.getDetalhes(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacao(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return resp(HttpStatus.BAD_REQUEST, "Dados inválidos", detalhes, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> corpoIlegivel(HttpMessageNotReadableException e, HttpServletRequest req) {
        return resp(HttpStatus.BAD_REQUEST, "Corpo da requisição ausente ou mal formatado", null, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tipoParametro(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return resp(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + e.getName(), null, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> negado(AccessDeniedException e, HttpServletRequest req) {
        return resp(HttpStatus.FORBIDDEN, "Você não tem permissão para acessar este recurso", null, req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridade(DataIntegrityViolationException e, HttpServletRequest req) {
        return resp(HttpStatus.CONFLICT, "Conflito de dados (valor duplicado ou vínculo existente)", null, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> geral(Exception e, HttpServletRequest req) {
        log.error("Erro não tratado", e);
        return resp(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", null, req);
    }

    private ResponseEntity<ApiError> resp(HttpStatus status, String msg, List<String> detalhes, HttpServletRequest req) {
        ApiError corpo = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), msg,
                detalhes == null ? List.of() : detalhes, req.getRequestURI());
        return ResponseEntity.status(status).body(corpo);
    }
}
