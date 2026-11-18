package cl.duoc.syncpass.sync.dto;

import java.util.List;

/** Cuerpo de {@code data} cuando el lote se rechaza completo. */
public record ErroresLote(List<LoteError> errores) {
}
