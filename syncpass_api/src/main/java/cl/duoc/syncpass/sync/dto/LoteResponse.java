package cl.duoc.syncpass.sync.dto;

/**
 * Contrato exacto que espera la app Android (Api.kt): { "aceptados": Int }.
 */
public record LoteResponse(int aceptados) {
}
