package cl.duoc.syncpass.sync.controller;

import cl.duoc.syncpass.sync.dto.EventoDto;
import cl.duoc.syncpass.sync.service.EventoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;

    @PostMapping
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<EventoDto> crear(@Valid @RequestBody EventoDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventoService.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Coordinador')")
    public EventoDto actualizar(@PathVariable Long id, @Valid @RequestBody EventoDto dto) {
        return eventoService.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        eventoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
