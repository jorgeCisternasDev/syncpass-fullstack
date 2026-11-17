package cl.duoc.syncpass.sync.config;

import cl.duoc.syncpass.sync.model.Evento;
import cl.duoc.syncpass.sync.model.Proyecto;
import cl.duoc.syncpass.sync.repository.EventoRepository;
import cl.duoc.syncpass.sync.repository.ProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(2)
@RequiredArgsConstructor
public class EventoSeeder implements CommandLineRunner {

    private final EventoRepository eventoRepository;
    private final ProyectoRepository proyectoRepository;

    @Override
    public void run(String... args) {
        if (eventoRepository.count() > 0) {
            return;
        }

        Proyecto verde = proyectoRepository.save(Proyecto.builder()
                .titulo("Proyecto Verde")
                .descripcion("Talleres de huerto comunitario y cuidado del entorno.")
                .areaTematica("Medio ambiente")
                .unidadVecinal("Villa Los Aromos")
                .fechaInicio(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli())
                .fechaTermino(Instant.parse("2026-12-31T23:59:59Z").toEpochMilli())
                .estado("EN_CURSO")
                .build());

        Proyecto conecta = proyectoRepository.save(Proyecto.builder()
                .titulo("Proyecto Conecta")
                .descripcion("Alfabetizacion digital para personas mayores.")
                .areaTematica("Inclusion digital")
                .unidadVecinal("Poblacion San Juan")
                .fechaInicio(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli())
                .fechaTermino(Instant.parse("2026-12-31T23:59:59Z").toEpochMilli())
                .estado("EN_CURSO")
                .build());

        eventoRepository.save(Evento.builder()
                .proyecto(verde)
                .nombre("Taller de huerto comunitario")
                .fechaHora(Instant.parse("2026-10-18T13:00:00Z").toEpochMilli())
                .cuposMax(30)
                .recinto("Sede vecinal Villa Los Aromos")
                .latitud(-33.0472)
                .longitud(-71.6127)
                .radioMetros(120)
                .build());

        eventoRepository.save(Evento.builder()
                .proyecto(conecta)
                .nombre("Jornada de alfabetizacion digital")
                .fechaHora(Instant.parse("2026-10-25T13:00:00Z").toEpochMilli())
                .cuposMax(25)
                .recinto("Biblioteca publica")
                .latitud(-33.0501)
                .longitud(-71.6089)
                .radioMetros(100)
                .build());
    }
}
