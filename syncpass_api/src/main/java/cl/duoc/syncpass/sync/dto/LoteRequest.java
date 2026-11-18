package cl.duoc.syncpass.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * Lote offline que envía la app. Contrato v2, sección 5.1.
 * Los campos desconocidos se ignoran para no romper clientes nuevos.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoteRequest {

    @Valid
    private List<@NotNull AsistenciaDto> asistencias;

    @Valid
    private List<@NotNull EncuestaDto> encuestas;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AsistenciaDto {

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un entero positivo")
        private Long eventoId;

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un entero positivo")
        private Long usuarioId;

        /** Opcional: {@code INSCRITO} o {@code PRESENTE}. Ausente se interpreta {@code PRESENTE}. */
        private String estado;

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un timestamp positivo")
        private Long timestamp;

        /** Se ignora: el servidor lo deriva del estado. */
        private Boolean validado;

        @DecimalMin(value = "-90.0", message = "debe estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "debe estar entre -90 y 90")
        private Double latitud;

        @DecimalMin(value = "-180.0", message = "debe estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "debe estar entre -180 y 180")
        private Double longitud;

        /** Atribución opcional (D-06 opción C): UUID local o {@code sub} de Cognito. */
        @Size(max = 64, message = "no puede superar 64 caracteres")
        @JsonDeserialize(using = TextoEstrictoDeserializer.class)
        private String usuarioUuid;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EncuestaDto {

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un entero positivo")
        private Long eventoId;

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un entero positivo")
        private Long usuarioId;

        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe estar entre 1 y 5")
        @Max(value = 5, message = "debe estar entre 1 y 5")
        private Integer satisfaccion;

        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe estar entre 1 y 5")
        @Max(value = 5, message = "debe estar entre 1 y 5")
        private Integer utilidad;

        @Size(max = 600, message = "no puede superar 600 caracteres")
        private String comentario;

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un timestamp positivo")
        private Long fecha;

        /** Atribución opcional (D-06 opción C): UUID local o {@code sub} de Cognito. */
        @Size(max = 64, message = "no puede superar 64 caracteres")
        @JsonDeserialize(using = TextoEstrictoDeserializer.class)
        private String usuarioUuid;
    }
}
