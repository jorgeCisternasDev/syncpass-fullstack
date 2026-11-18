package cl.duoc.syncpass.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Encuesta de satisfacción: una por evento y clave opaca de usuario.
 * El comentario admite 600 caracteres, igual que la app (contrato v2, sección 5.1).
 */
@Entity
@Table(name = "encuestas", uniqueConstraints = @UniqueConstraint(columnNames = { "evento_id", "usuario_id" }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Encuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Atribución opcional (D-06 opción C). */
    @Column(name = "usuario_uuid", length = 64)
    private String usuarioUuid;

    @Column(nullable = false)
    private Integer satisfaccion;

    @Column(nullable = false)
    private Integer utilidad;

    @Column(length = 600)
    private String comentario;

    @Column(name = "fecha_ms", nullable = false)
    private Long fecha;
}
