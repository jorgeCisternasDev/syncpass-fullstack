package cl.duoc.syncpass.sync;

import cl.duoc.syncpass.sync.controller.SyncController;
import cl.duoc.syncpass.sync.dto.LoteRequest;
import cl.duoc.syncpass.sync.dto.LoteResponse;
import cl.duoc.syncpass.sync.model.AsistenciaId;
import cl.duoc.syncpass.sync.repository.AsistenciaRepository;
import cl.duoc.syncpass.sync.repository.EncuestaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    @Mock
    private AsistenciaRepository asistenciaRepository;

    @Mock
    private EncuestaRepository encuestaRepository;

    @InjectMocks
    private SyncController controller;

    @Test
    @DisplayName("Una asistencia ya guardada se cuenta como aceptada pero no se duplica")
    void asistenciaRepetidaNoDuplica() {
        LoteRequest.AsistenciaDto dto = new LoteRequest.AsistenciaDto();
        dto.setEventoId(1L);
        dto.setUsuarioId(101L);
        dto.setTimestamp(1759600000000L);
        dto.setValidado(true);

        when(asistenciaRepository.existsById(ArgumentMatchers.any(AsistenciaId.class))).thenReturn(true);

        LoteRequest lote = new LoteRequest();
        lote.setAsistencias(List.of(dto));

        LoteResponse response = controller.recibirLote(lote).getBody();

        assertNotNull(response);
        assertEquals(1, response.aceptados());
        verify(asistenciaRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Una encuesta repetida no se vuelve a guardar")
    void encuestaRepetidaNoDuplica() {
        LoteRequest.EncuestaDto dto = new LoteRequest.EncuestaDto();
        dto.setEventoId(1L);
        dto.setUsuarioId(101L);
        dto.setSatisfaccion(5);
        dto.setUtilidad(4);
        dto.setFecha(1759600600000L);

        when(encuestaRepository.findByEventoIdAndUsuarioId(1L, 101L)).thenReturn(Optional.of(new cl.duoc.syncpass.sync.model.Encuesta()));

        LoteRequest lote = new LoteRequest();
        lote.setEncuestas(List.of(dto));

        LoteResponse response = controller.recibirLote(lote).getBody();

        assertNotNull(response);
        assertEquals(1, response.aceptados());
        verify(encuestaRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Un lote nuevo persiste asistencias y encuestas")
    void loteNuevoPersisteTodo() {
        LoteRequest.AsistenciaDto asistencia = new LoteRequest.AsistenciaDto();
        asistencia.setEventoId(2L);
        asistencia.setUsuarioId(200L);
        asistencia.setTimestamp(1759600000000L);
        asistencia.setValidado(true);

        LoteRequest.EncuestaDto encuesta = new LoteRequest.EncuestaDto();
        encuesta.setEventoId(2L);
        encuesta.setUsuarioId(200L);
        encuesta.setSatisfaccion(4);
        encuesta.setUtilidad(3);
        encuesta.setFecha(1759600600000L);

        when(asistenciaRepository.existsById(ArgumentMatchers.any(AsistenciaId.class))).thenReturn(false);
        when(encuestaRepository.findByEventoIdAndUsuarioId(2L, 200L)).thenReturn(Optional.empty());

        LoteRequest lote = new LoteRequest();
        lote.setAsistencias(List.of(asistencia));
        lote.setEncuestas(List.of(encuesta));

        LoteResponse response = controller.recibirLote(lote).getBody();

        assertNotNull(response);
        assertEquals(2, response.aceptados());
        verify(asistenciaRepository).save(ArgumentMatchers.any());
        verify(encuestaRepository).save(ArgumentMatchers.any());
    }
}
