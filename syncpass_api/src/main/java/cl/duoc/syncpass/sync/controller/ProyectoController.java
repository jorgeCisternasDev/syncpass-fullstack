package cl.duoc.syncpass.sync.controller;

import cl.duoc.syncpass.sync.dto.EventoDto;
import cl.duoc.syncpass.sync.dto.ProyectoDto;
import cl.duoc.syncpass.sync.service.EventoService;
import cl.duoc.syncpass.sync.service.ProyectoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/proyectos")
@RequiredArgsConstructor
public class ProyectoController {

    private final ProyectoService proyectoService;
    private final EventoService eventoService;

    @GetMapping
    @PreAuthorize("hasAnyRole('Coordinador', 'Monitor', 'Asistente')")
    public List<ProyectoDto> listar() {
        return proyectoService.listar();
    }

    @PostMapping
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<ProyectoDto> crear(@Valid @RequestBody ProyectoDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proyectoService.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Coordinador')")
    public ProyectoDto actualizar(@PathVariable Long id, @Valid @RequestBody ProyectoDto dto) {
        return proyectoService.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        proyectoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/eventos")
    @PreAuthorize("hasAnyRole('Coordinador', 'Monitor', 'Asistente')")
    public List<EventoDto> listarEventos(@PathVariable Long id) {
        return eventoService.listarPorProyecto(id);
    }
}
