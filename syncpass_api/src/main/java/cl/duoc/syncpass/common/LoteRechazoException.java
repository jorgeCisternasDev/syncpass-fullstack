package cl.duoc.syncpass.common;

import cl.duoc.syncpass.sync.dto.LoteError;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Rechazo de un lote completo. Lleva el detalle por ítem que exige el contrato v2 (5.4).
 * Al ser una excepción no comprobada, marca la transacción para rollback: el lote es atómico.
 */
@Getter
public class LoteRechazoException extends RuntimeException {

    private final HttpStatus status;
    private final List<LoteError> errores;

    public LoteRechazoException(HttpStatus status, String message, List<LoteError> errores) {
        super(message);
        this.status = status;
        this.errores = List.copyOf(errores);
    }

    /** 409: el lote completo no se aplica. */
    public static LoteRechazoException conflicto(List<LoteError> errores) {
        return new LoteRechazoException(HttpStatus.CONFLICT, resumen(errores), errores);
    }

    /** 409 de un solo ítem, con el mensaje de negocio exacto del contrato. */
    public static LoteRechazoException conflicto(LoteError error) {
        return conflicto(List.of(error));
    }

    private static String resumen(List<LoteError> errores) {
        int total = errores.size();
        return "El lote no se aplicó: " + total + (total == 1 ? " ítem rechazado." : " ítems rechazados.");
    }
}
