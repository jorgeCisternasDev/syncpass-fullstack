package cl.duoc.syncpass.common;

import cl.duoc.syncpass.participantes.ParticipanteException;
import cl.duoc.syncpass.participantes.dto.ParticipanteError;
import cl.duoc.syncpass.sync.dto.ErroresLote;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage()));
    }

    /** Aprovisionamiento de participantes: el código de negocio viaja en {@code data.codigo}. */
    @ExceptionHandler(ParticipanteException.class)
    public ResponseEntity<ApiResponse<ParticipanteError>> handleParticipante(ParticipanteException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.of(false, new ParticipanteError(ex.getCodigo()), ex.getMessage()));
    }

    /** Rechazo atómico del lote: 409 (o 400 de dominio) con el detalle por ítem. */
    @ExceptionHandler(LoteRechazoException.class)
    public ResponseEntity<ApiResponse<ErroresLote>> handleLote(LoteRechazoException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.of(false, new ErroresLote(ex.getErrores()), ex.getMessage()));
    }

    /** Cuerpo ilegible: tipo equivocado, JSON malformado o enum desconocido (contrato v2, 5.2). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleFormato(HttpMessageNotReadableException ex,
                                                           HttpServletRequest request) {
        String uri = request.getRequestURI();
        String mensaje = uri != null && uri.startsWith("/api/sync/")
                ? "El lote no tiene el formato esperado."
                : "El cuerpo de la solicitud no tiene el formato esperado.";
        return ResponseEntity.badRequest().body(ApiResponse.error(mensaje));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.error(detail));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("No tiene permisos para realizar esta acción"));
    }

    private String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
