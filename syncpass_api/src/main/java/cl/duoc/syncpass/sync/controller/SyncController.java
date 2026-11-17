package cl.duoc.syncpass.sync.controller;

import cl.duoc.syncpass.sync.dto.LoteRequest;
import cl.duoc.syncpass.sync.dto.LoteResponse;
import cl.duoc.syncpass.sync.model.Asistencia;
import cl.duoc.syncpass.sync.model.AsistenciaId;
import cl.duoc.syncpass.sync.model.Encuesta;
import cl.duoc.syncpass.sync.repository.AsistenciaRepository;
import cl.duoc.syncpass.sync.repository.EncuestaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recepción de lotes offline enviados por la app Android.
 * Idempotente: reenviar el mismo lote no duplica ni falla.
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;

    @PostMapping("/lote")
    @Transactional
    public ResponseEntity<LoteResponse> recibirLote(@RequestBody LoteRequest lote) {
        int aceptados = 0;

        List<LoteRequest.AsistenciaDto> asistencias = lote.getAsistencias();
        if (asistencias != null) {
            for (LoteRequest.AsistenciaDto dto : asistencias) {
                AsistenciaId id = AsistenciaId.builder()
                        .eventoId(dto.getEventoId())
                        .usuarioId(dto.getUsuarioId())
                        .build();

                if (asistenciaRepository.existsById(id)) {
                    aceptados++;
                    continue; // ya persistida: idempotente
                }

                asistenciaRepository.save(Asistencia.builder()
                        .id(id)
                        .timestamp(dto.getTimestamp() == null ? System.currentTimeMillis() : dto.getTimestamp())
                        .validado(dto.isValidado())
                        .latitud(dto.getLatitud())
                        .longitud(dto.getLongitud())
                        .build());
                aceptados++;
            }
        }

        List<LoteRequest.EncuestaDto> encuestas = lote.getEncuestas();
        if (encuestas != null) {
            for (LoteRequest.EncuestaDto dto : encuestas) {
                boolean existe = encuestaRepository
                        .findByEventoIdAndUsuarioId(dto.getEventoId(), dto.getUsuarioId())
                        .isPresent();
                if (existe) {
                    aceptados++;
                    continue; // una encuesta por evento + usuario
                }

                encuestaRepository.save(Encuesta.builder()
                        .eventoId(dto.getEventoId())
                        .usuarioId(dto.getUsuarioId())
                        .satisfaccion(dto.getSatisfaccion())
                        .utilidad(dto.getUtilidad())
                        .comentario(dto.getComentario())
                        .fecha(dto.getFecha() == null ? System.currentTimeMillis() : dto.getFecha())
                        .build());
                aceptados++;
            }
        }

        return ResponseEntity.ok(new LoteResponse(aceptados));
    }
}
