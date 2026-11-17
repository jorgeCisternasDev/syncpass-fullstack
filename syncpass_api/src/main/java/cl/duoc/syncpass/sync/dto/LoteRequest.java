package cl.duoc.syncpass.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoteRequest {

    private List<AsistenciaDto> asistencias;
    private List<EncuestaDto> encuestas;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AsistenciaDto {
        private Long eventoId;
        private Long usuarioId;
        private Long timestamp;
        private boolean validado;
        private Double latitud;
        private Double longitud;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EncuestaDto {
        private Long eventoId;
        private Long usuarioId;
        private Integer satisfaccion;
        private Integer utilidad;
        private String comentario;
        private Long fecha;
    }
}
