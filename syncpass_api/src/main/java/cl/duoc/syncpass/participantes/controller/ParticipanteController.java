package cl.duoc.syncpass.participantes.controller;

import cl.duoc.syncpass.participantes.dto.ParticipanteRequest;
import cl.duoc.syncpass.participantes.dto.ParticipanteResponse;
import cl.duoc.syncpass.participantes.service.ParticipanteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/participantes} (H3-DISENO 2.2).
 * El acceso lo exige {@link cl.duoc.syncpass.config.SecurityConfig}: Bearer con rol
 * Coordinador, Monitor o Asistente.
 */
@RestController
@RequestMapping("/api/participantes")
@RequiredArgsConstructor
public class ParticipanteController {

    private final ParticipanteService participanteService;

    @PostMapping
    public ParticipanteResponse registrar(@Valid @RequestBody ParticipanteRequest request) {
        return participanteService.registrar(request);
    }
}
