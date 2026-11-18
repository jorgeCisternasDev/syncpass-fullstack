package cl.duoc.syncpass.sync.dto;

/** Un ítem rechazado del lote (contrato v2, sección 5.5). */
public record LoteError(int indice, String tipo, String codigo, String mensaje) {
}
