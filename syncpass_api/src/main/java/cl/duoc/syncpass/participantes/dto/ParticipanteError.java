package cl.duoc.syncpass.participantes.dto;

/**
 * Detalle del error de aprovisionamiento, en {@code data.codigo}, con el estilo del
 * {@code data.errores[].codigo} del contrato v2.
 */
public record ParticipanteError(String codigo) {
}
