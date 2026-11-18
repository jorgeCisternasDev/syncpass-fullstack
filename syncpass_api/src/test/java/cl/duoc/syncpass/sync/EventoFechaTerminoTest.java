package cl.duoc.syncpass.sync;

import cl.duoc.syncpass.sync.config.EventoSeeder;
import cl.duoc.syncpass.sync.model.Evento;
import cl.duoc.syncpass.sync.repository.AsistenciaRepository;
import cl.duoc.syncpass.sync.repository.EncuestaRepository;
import cl.duoc.syncpass.sync.repository.EventoRepository;
import cl.duoc.syncpass.sync.repository.ProyectoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extensión aditiva de H3 (H3-DISENO 3.2): {@code EventoDto.fechaTermino} opcional.
 * El servidor no inventa duración; el seeder local usa {@code fechaHora + 2 h} (S-2).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-key-for-integration-32b")
class EventoFechaTerminoTest {

    private static final long FECHA_HORA = 1_794_000_000_000L;
    private static final long DOS_HORAS = Duration.ofHours(2).toMillis();

    @Value("${local.server.port}")
    private int port;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private EventoSeeder eventoSeeder;

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private AsistenciaRepository asistenciaRepository;

    @Autowired
    private EncuestaRepository encuestaRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private Long proyectoId;

    @BeforeEach
    void preparar() throws Exception {
        encuestaRepository.deleteAll();
        asistenciaRepository.deleteAll();
        eventoRepository.deleteAll();
        proyectoRepository.deleteAll();

        Respuesta proyecto = post("/api/proyectos", token("Coordinador"), """
                {
                  "titulo": "Proyecto ficticio de fechaTermino",
                  "descripcion": "Datos ficticios para pruebas automatizadas",
                  "areaTematica": "Pruebas",
                  "unidadVecinal": "Sin datos reales",
                  "fechaInicio": 1700000000000,
                  "fechaTermino": 1900000000000,
                  "estado": "EN_CURSO"
                }
                """);
        assertEquals(201, proyecto.status(), proyecto.cuerpo().toString());
        proyectoId = proyecto.cuerpo().get("proyectoId").longValue();
    }

    @Test
    @DisplayName("El evento acepta fechaTermino y la devuelve en el contrato plano")
    void fechaTerminoSeGuardaYDevuelve() throws Exception {
        long termino = FECHA_HORA + DOS_HORAS;

        Respuesta creado = post("/api/eventos", token("Coordinador"), evento(FECHA_HORA, termino));

        assertEquals(201, creado.status(), creado.cuerpo().toString());
        assertEquals(termino, creado.cuerpo().get("fechaTermino").longValue());
        assertTrue(!creado.cuerpo().toString().contains("\"success\""), creado.cuerpo().toString());

        long eventoId = creado.cuerpo().get("eventoId").longValue();
        assertEquals(termino, eventoRepository.findById(eventoId).orElseThrow().getFechaTermino());

        Respuesta listado = get("/api/proyectos/" + proyectoId + "/eventos", token("Monitor"));
        assertEquals(200, listado.status(), listado.cuerpo().toString());

        JsonNode encontrado = null;
        for (JsonNode item : listado.cuerpo()) {
            if (item.get("eventoId").longValue() == eventoId) {
                encontrado = item;
            }
        }
        assertNotNull(encontrado, listado.cuerpo().toString());
        assertEquals(termino, encontrado.get("fechaTermino").longValue());
    }

    @Test
    @DisplayName("Sin fechaTermino el evento queda nulo: el servidor no inventa la duración")
    void fechaTerminoOpcionalQuedaNula() throws Exception {
        Respuesta creado = post("/api/eventos", token("Coordinador"), evento(FECHA_HORA, null));

        assertEquals(201, creado.status(), creado.cuerpo().toString());

        JsonNode campo = creado.cuerpo().get("fechaTermino");
        assertTrue(campo == null || campo.isNull(), creado.cuerpo().toString());

        long eventoId = creado.cuerpo().get("eventoId").longValue();
        assertNull(eventoRepository.findById(eventoId).orElseThrow().getFechaTermino());
    }

    @Test
    @DisplayName("El seeder usa fechaHora + 2 h cuando no declara fechaTermino")
    void seederUsaDosHoras() {
        eventoSeeder.run();

        List<Evento> eventos = eventoRepository.findAll();
        assertEquals(2, eventos.size(), "eventos sembrados");
        for (Evento evento : eventos) {
            assertNotNull(evento.getFechaTermino(), evento.getNombre());
            assertEquals(evento.getFechaHora() + DOS_HORAS, evento.getFechaTermino(), evento.getNombre());
        }
    }

    // ------------------------------------------------------------------ helpers

    private String evento(long fechaHora, Long fechaTermino) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("proyectoId", proyectoId);
        dto.put("nombre", "Evento ficticio de fechaTermino");
        dto.put("fechaHora", fechaHora);
        if (fechaTermino != null) {
            dto.put("fechaTermino", fechaTermino);
        }
        dto.put("cuposMax", 20);
        dto.put("recinto", "Recinto ficticio");
        dto.put("latitud", -33.05);
        dto.put("longitud", -71.61);
        dto.put("radioMetros", 80);
        return mapper.writeValueAsString(dto);
    }

    private Respuesta post(String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return enviar(builder.build());
    }

    private Respuesta get(String ruta, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + ruta))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return enviar(builder.build());
    }

    private Respuesta enviar(HttpRequest request) throws Exception {
        HttpResponse<String> respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = respuesta.body() == null || respuesta.body().isBlank()
                ? null
                : mapper.readTree(respuesta.body());
        return new Respuesta(respuesta.statusCode(), json);
    }

    private String token(String... roles) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("usuario-ficticio-de-pruebas")
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(3600))
                .claim("roles", List.of(roles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private record Respuesta(int status, JsonNode cuerpo) {
    }
}
