package cl.duoc.syncpass.sync.repository;

import cl.duoc.syncpass.sync.model.Asistencia;
import cl.duoc.syncpass.sync.model.AsistenciaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsistenciaRepository extends JpaRepository<Asistencia, AsistenciaId> {

    /** Cupo: se cuentan filas del evento en cualquier estado (contrato v2, 4.1). */
    @Query("select count(a) from Asistencia a where a.id.eventoId = :eventoId")
    long contarPorEvento(@Param("eventoId") Long eventoId);
}
