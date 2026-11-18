package cl.duoc.syncpass.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Asistencia de un participante a un evento.
 * Clave opaca {@code (evento_id, usuario_id)} (contrato v2, sección 2.3).
 */
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

    /** Atribución opcional (D-06 opción C). Nula mientras no exista aprovisionamiento (H3). */
    @Column(name = "usuario_uuid", length = 64)
    private String usuarioUuid;

    /** Solo {@code INSCRITO} o {@code PRESENTE}. Nunca {@code PENDIENTE}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    @Builder.Default
    private EstadoAsistencia estado = EstadoAsistencia.PRESENTE;

    /** Milisegundos desde epoch, tal como los envía la app (puede venir horas después). */
    @Column(name = "timestamp_ms", nullable = false)
    private Long timestamp;

    /** Momento original de la inscripción; solo se llena al pasar de INSCRITO a PRESENTE. */
    @Column(name = "inscrito_ms")
    private Long inscritoMs;

    /** Se conserva por compatibilidad, pero el servidor lo recalcula desde {@link #estado}. */
    @Column(nullable = false)
    private boolean validado;

    private Double latitud;

    private Double longitud;
}
