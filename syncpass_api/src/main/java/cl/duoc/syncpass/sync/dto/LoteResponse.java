package cl.duoc.syncpass.sync.dto;

import java.util.List;

/**
 * Respuesta plana del lote (contrato v2, sección 5.5).
 * <p>
 * {@code aceptados = nuevos + actualizados + duplicados}. {@code atribuidos} no suma a
 * {@code aceptados}: cuenta filas que recibieron {@code usuario_uuid} en esta petición.
 * En un 200, {@code rechazados} es siempre 0 y {@code errores} viene vacío.
 */
public record LoteResponse(
        int aceptados,
        int nuevos,
        int actualizados,
        int duplicados,
        int atribuidos,
        int rechazados,
        List<LoteError> errores
) {
}
