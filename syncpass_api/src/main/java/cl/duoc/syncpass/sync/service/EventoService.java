package cl.duoc.syncpass.sync.service;

import cl.duoc.syncpass.common.BusinessException;
import cl.duoc.syncpass.sync.dto.EventoDto;
import cl.duoc.syncpass.sync.model.Evento;
import cl.duoc.syncpass.sync.model.Proyecto;
import cl.duoc.syncpass.sync.repository.EventoRepository;
import cl.duoc.syncpass.sync.repository.ProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventoService {

    private final EventoRepository eventoRepository;
    private final ProyectoRepository proyectoRepository;

    public List<EventoDto> listarPorProyecto(Long proyectoId) {
        buscarProyecto(proyectoId);
        return eventoRepository.findByProyectoIdOrderByFechaHoraAsc(proyectoId)
                .stream().map(this::toDto).toList();
    }

    public EventoDto obtener(Long id) {
        return toDto(buscar(id));
    }

    @Transactional
    public EventoDto crear(EventoDto dto) {
        Proyecto proyecto = buscarProyecto(dto.proyectoId());
        validarFecha(dto.fechaHora(), proyecto);

        Evento evento = Evento.builder()
                .proyecto(proyecto)
                .nombre(dto.nombre().trim())
                .fechaHora(dto.fechaHora())
                .cuposMax(dto.cuposMax())
                .recinto(dto.recinto().trim())
                .latitud(dto.latitud())
                .longitud(dto.longitud())
                .radioMetros(dto.radioMetros())
                .build();

        return toDto(eventoRepository.save(evento));
    }

    @Transactional
    public EventoDto actualizar(Long id, EventoDto dto) {
        Evento evento = buscar(id);
        Proyecto proyecto = buscarProyecto(dto.proyectoId());
        validarFecha(dto.fechaHora(), proyecto);

        evento.setProyecto(proyecto);
        evento.setNombre(dto.nombre().trim());
        evento.setFechaHora(dto.fechaHora());
        evento.setCuposMax(dto.cuposMax());
        evento.setRecinto(dto.recinto().trim());
        evento.setLatitud(dto.latitud());
        evento.setLongitud(dto.longitud());
        evento.setRadioMetros(dto.radioMetros());

        return toDto(eventoRepository.save(evento));
    }

    @Transactional
    public void eliminar(Long id) {
        eventoRepository.delete(buscar(id));
    }

    private Evento buscar(Long id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Evento no encontrado: " + id));
    }

    private Proyecto buscarProyecto(Long proyectoId) {
        return proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> BusinessException.notFound("Proyecto no encontrado: " + proyectoId));
    }

    private void validarFecha(Long fechaHora, Proyecto proyecto) {
        if (fechaHora == null) {
            throw BusinessException.badRequest("fechaHora es obligatoria");
        }
        if (fechaHora < proyecto.getFechaInicio() || fechaHora > proyecto.getFechaTermino()) {
            throw BusinessException.badRequest("fechaHora debe estar dentro del rango del proyecto");
        }
    }

    private EventoDto toDto(Evento evento) {
        return new EventoDto(
                evento.getId(),
                evento.getProyecto().getId(),
                evento.getNombre(),
                evento.getFechaHora(),
                evento.getCuposMax(),
                evento.getRecinto(),
                evento.getLatitud(),
                evento.getLongitud(),
                evento.getRadioMetros()
        );
    }
}
