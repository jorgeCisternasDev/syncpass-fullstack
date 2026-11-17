package cl.duoc.syncpass.sync.repository;

import cl.duoc.syncpass.sync.model.Asistencia;
import cl.duoc.syncpass.sync.model.AsistenciaId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsistenciaRepository extends JpaRepository<Asistencia, AsistenciaId> {
}
