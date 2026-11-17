package cl.duoc.syncpass.sync.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Contrato exacto de {@code EventoRemoto} en Api.kt (app Android).
 * Se devuelve sin envoltorio.
 */
public record EventoDto(
        Long eventoId,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        Long proyectoId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String nombre,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un timestamp positivo")
        Long fechaHora,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        Integer cuposMax,

        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String recinto,

        @DecimalMin(value = "-90.0", message = "debe estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "debe estar entre -90 y 90")
        Double latitud,

        @DecimalMin(value = "-180.0", message = "debe estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "debe estar entre -180 y 180")
        Double longitud,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        Integer radioMetros
) {
}
