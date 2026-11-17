package cl.duoc.syncpass.sync.controller;

import cl.duoc.syncpass.auth.repository.UsuarioRepository;
import cl.duoc.syncpass.common.ApiResponse;
import cl.duoc.syncpass.sync.repository.AsistenciaRepository;
import cl.duoc.syncpass.sync.repository.EncuestaRepository;
import cl.duoc.syncpass.sync.repository.EventoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UsuarioRepository usuarioRepository;
    private final EventoRepository eventoRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;

    @GetMapping("/resumen")
    public ResponseEntity<ApiResponse<Map<String, Long>>> resumen() {
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("usuarios", usuarioRepository.count());
        data.put("eventos", eventoRepository.count());
        data.put("asistencias", asistenciaRepository.count());
        data.put("encuestas", encuestaRepository.count());

        return ResponseEntity.ok(ApiResponse.ok(data, "Resumen del sistema"));
    }
}
