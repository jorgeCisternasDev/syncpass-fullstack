package cl.duoc.syncpass.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "eventos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "fecha_hora", nullable = false)
    private Long fechaHora;

    @Column(name = "cupos_max", nullable = false)
    private Integer cuposMax;

    @Column(nullable = false)
    private String recinto;

    private Double latitud;

    private Double longitud;

    @Column(name = "radio_metros", nullable = false)
    private Integer radioMetros;
}
