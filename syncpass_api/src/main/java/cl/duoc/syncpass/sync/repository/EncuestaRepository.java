package cl.duoc.syncpass.sync.repository;

import cl.duoc.syncpass.sync.model.Encuesta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EncuestaRepository extends JpaRepository<Encuesta, Long> {

    Optional<Encuesta> findByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);
}
