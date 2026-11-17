package cl.duoc.syncpass.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Contrato exacto de {@code ProyectoRemoto} en Api.kt (app Android).
 * Se devuelve sin envoltorio.
 */
public record ProyectoDto(
        Long proyectoId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String titulo,

        @NotBlank(message = "es obligatoria")
        @Size(max = 1000, message = "no puede superar 1000 caracteres")
        String descripcion,

        @NotBlank(message = "es obligatoria")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String areaTematica,

        @NotBlank(message = "es obligatoria")
        @Size(max = 160, message = "no puede superar 160 caracteres")
        String unidadVecinal,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un timestamp positivo")
        Long fechaInicio,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un timestamp positivo")
        Long fechaTermino,

        @NotBlank(message = "es obligatorio")
        @Size(max = 40, message = "no puede superar 40 caracteres")
        String estado
) {
}
