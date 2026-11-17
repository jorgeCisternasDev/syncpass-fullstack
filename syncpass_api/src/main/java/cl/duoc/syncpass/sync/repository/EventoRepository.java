package cl.duoc.syncpass.sync.repository;

import cl.duoc.syncpass.sync.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findByProyectoIdOrderByFechaHoraAsc(Long proyectoId);

    boolean existsByProyectoId(Long proyectoId);
}
