package cl.duoc.syncpass.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AsistenciaId implements Serializable {

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
}
