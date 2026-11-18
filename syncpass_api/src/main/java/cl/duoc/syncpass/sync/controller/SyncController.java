package cl.duoc.syncpass.sync.controller;

import cl.duoc.syncpass.sync.dto.LoteRequest;
import cl.duoc.syncpass.sync.dto.LoteResponse;
import cl.duoc.syncpass.sync.service.LoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recepción de lotes offline enviados por la app Android.
 * La respuesta es plana, como exige el contrato; las reglas viven en {@link LoteService}.
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final LoteService loteService;

    @PostMapping("/lote")
    public LoteResponse recibirLote(@Valid @RequestBody LoteRequest lote) {
        return loteService.procesar(lote);
    }
}
