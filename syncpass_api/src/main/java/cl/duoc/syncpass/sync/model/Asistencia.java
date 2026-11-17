package cl.duoc.syncpass.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "asistencias")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asistencia {

    @EmbeddedId
    private AsistenciaId id;

    /** Milisegundos desde epoch, tal como los envía la app (puede venir horas después). */
    @Column(name = "timestamp_ms", nullable = false)
    private Long timestamp;

    @Column(nullable = false)
    private boolean validado;

    private Double latitud;

    private Double longitud;
}
