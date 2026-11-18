package cl.duoc.syncpass.sync.repository;

import cl.duoc.syncpass.sync.model.Evento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findByProyectoIdOrderByFechaHoraAsc(Long proyectoId);

    boolean existsByProyectoId(Long proyectoId);

    /**
     * Bloqueo pesimista sobre la fila del evento (contrato v2, 4.4).
     * Se pide {@code SELECT ... FOR UPDATE} dentro de la transacción del lote para que dos
     * teléfonos no puedan superar el cupo al mismo tiempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evento e where e.id = :id")
    Optional<Evento> findByIdForUpdate(@Param("id") Long id);
}
