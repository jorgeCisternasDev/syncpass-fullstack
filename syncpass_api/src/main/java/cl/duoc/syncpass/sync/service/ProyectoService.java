package cl.duoc.syncpass.sync.service;

import cl.duoc.syncpass.common.BusinessException;
import cl.duoc.syncpass.sync.dto.ProyectoDto;
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
public class ProyectoService {

    private final ProyectoRepository proyectoRepository;
    private final EventoRepository eventoRepository;

    public List<ProyectoDto> listar() {
        return proyectoRepository.findAll().stream().map(this::toDto).toList();
    }

    public ProyectoDto obtener(Long id) {
        return toDto(buscar(id));
    }

    @Transactional
    public ProyectoDto crear(ProyectoDto dto) {
        validarFechas(dto.fechaInicio(), dto.fechaTermino());

        Proyecto proyecto = Proyecto.builder()
                .titulo(dto.titulo().trim())
                .descripcion(dto.descripcion().trim())
                .areaTematica(dto.areaTematica().trim())
                .unidadVecinal(dto.unidadVecinal().trim())
                .fechaInicio(dto.fechaInicio())
                .fechaTermino(dto.fechaTermino())
                .estado(dto.estado().trim())
                .build();

        return toDto(proyectoRepository.save(proyecto));
    }

    @Transactional
    public ProyectoDto actualizar(Long id, ProyectoDto dto) {
        Proyecto proyecto = buscar(id);
        validarFechas(dto.fechaInicio(), dto.fechaTermino());

        proyecto.setTitulo(dto.titulo().trim());
        proyecto.setDescripcion(dto.descripcion().trim());
        proyecto.setAreaTematica(dto.areaTematica().trim());
        proyecto.setUnidadVecinal(dto.unidadVecinal().trim());
        proyecto.setFechaInicio(dto.fechaInicio());
        proyecto.setFechaTermino(dto.fechaTermino());
        proyecto.setEstado(dto.estado().trim());

        return toDto(proyectoRepository.save(proyecto));
    }

    @Transactional
    public void eliminar(Long id) {
        Proyecto proyecto = buscar(id);
        if (eventoRepository.existsByProyectoId(id)) {
            throw BusinessException.conflict("No se puede eliminar un proyecto con eventos asociados");
        }
        proyectoRepository.delete(proyecto);
    }

    public Proyecto buscar(Long id) {
        return proyectoRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Proyecto no encontrado: " + id));
    }

    private void validarFechas(Long inicio, Long termino) {
        if (inicio != null && termino != null && termino < inicio) {
            throw BusinessException.badRequest("fechaTermino debe ser mayor o igual que fechaInicio");
        }
    }

    private ProyectoDto toDto(Proyecto proyecto) {
        return new ProyectoDto(
                proyecto.getId(),
                proyecto.getTitulo(),
                proyecto.getDescripcion(),
                proyecto.getAreaTematica(),
                proyecto.getUnidadVecinal(),
                proyecto.getFechaInicio(),
                proyecto.getFechaTermino(),
                proyecto.getEstado()
        );
    }
}
