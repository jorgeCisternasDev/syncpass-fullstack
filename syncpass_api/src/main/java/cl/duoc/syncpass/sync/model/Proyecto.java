package cl.duoc.syncpass.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "proyectos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    @Column(name = "area_tematica", nullable = false)
    private String areaTematica;

    @Column(name = "unidad_vecinal", nullable = false)
    private String unidadVecinal;

    @Column(name = "fecha_inicio", nullable = false)
    private Long fechaInicio;

    @Column(name = "fecha_termino", nullable = false)
    private Long fechaTermino;

    @Column(nullable = false)
    private String estado;
}
