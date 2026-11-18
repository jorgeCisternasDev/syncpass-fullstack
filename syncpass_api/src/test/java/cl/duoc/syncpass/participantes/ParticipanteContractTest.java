package cl.duoc.syncpass.participantes;

import cl.duoc.syncpass.auth.model.Usuario;
import cl.duoc.syncpass.auth.repository.UsuarioRepository;
import cl.duoc.syncpass.sync.model.Evento;
import cl.duoc.syncpass.sync.model.Proyecto;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Casos P-01 a P-06 del aprovisionamiento de participantes (H3-DISENO 5) contra la API real,
 * con H2 y datos ficticios. Cada caso prepara su propio evento y no toca datos de otros eventos.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-key-for-integration-32b")
class ParticipanteContractTest {

    private static final String P1 = "11111111-1111-4111-8111-111111111111";
    private static final String P2 = "22222222-2222-4222-8222-222222222222";
    private static final String P3 = "33333333-3333-4333-8333-333333333333";
    private static final String P4 = "44444444-4444-4444-8444-444444444444";

    private static final String NOMBRE = "Participante Ficticio";
    private static final String EMAIL = "ficticio@syncpass.local";

    @Value("${local.server.port}")
    private int port;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UsuarioRepository usuarioRepository;

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

    private Long eventoId;

    @BeforeEach
    void preparar() {
        encuestaRepository.deleteAll();
        asistenciaRepository.deleteAll();
        eventoRepository.deleteAll();
        proyectoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Proyecto proyecto = proyectoRepository.save(Proyecto.builder()
                .titulo("Proyecto ficticio de aprovisionamiento")
                .descripcion("Datos ficticios para pruebas automatizadas")
                .areaTematica("Pruebas")
                .unidadVecinal("Sin datos reales")
                .fechaInicio(1_700_000_000_000L)
                .fechaTermino(1_900_000_000_000L)
                .estado("EN_CURSO")
                .build());

        eventoId = eventoRepository.save(Evento.builder()
                .proyecto(proyecto)
                .nombre("Evento ficticio de aprovisionamiento")
                .fechaHora(1_794_000_000_000L)
                .fechaTermino(1_794_007_200_000L)
                .cuposMax(30)
                .recinto("Recinto ficticio")
                .radioMetros(100)
                .build()).getId();
    }

    // ------------------------------------------------------------------ P-01

    @Test
    @DisplayName("P-01 UUID nuevo: 200, creado=true, usuario Asistente sin password")
    void p01ProvisionaUuidNuevo() throws Exception {
        Respuesta r = post("/api/participantes", token("Monitor"),
                participante(P1, NOMBRE, EMAIL));

        assertEquals(200, r.status());
        assertEquals(P1, r.cuerpo().get("usuarioUuid").stringValue());
        assertTrue(r.cuerpo().get("creado").booleanValue(), r.cuerpo().toString());

        Usuario creado = usuarioRepository.findById(P1).orElseThrow();
        assertEquals(NOMBRE, creado.getNombre());
        assertEquals(EMAIL, creado.getEmail());
        assertEquals(Set.of("Asistente"), creado.getRoles());
        assertTrue(creado.isActivo());
        assertNull(creado.getPasswordHash());
        assertEquals(1, usuarioRepository.count());
    }

    @Test
    @DisplayName("P-01 email opcional: usa participante+<uuid>@syncpass.local")
    void p01bEmailOpcionalUsaSintetico() throws Exception {
        Respuesta r = post("/api/participantes", token("Monitor"),
                participante(P2, NOMBRE, null));

        assertEquals(200, r.status());
        assertTrue(r.cuerpo().get("creado").booleanValue(), r.cuerpo().toString());

        Usuario creado = usuarioRepository.findById(P2).orElseThrow();
        assertEquals("participante+" + P2 + "@syncpass.local", creado.getEmail());
    }

    // ------------------------------------------------------------------ P-02

    @Test
    @DisplayName("P-02 repetir el mismo UUID: 200, creado=false y sin duplicar")
    void p02RepetirMismoUuidNoDuplica() throws Exception {
        Map<String, Object> cuerpo = participante(P1, NOMBRE, EMAIL);

        assertEquals(200, post("/api/participantes", token("Monitor"), cuerpo).status());
        Respuesta r = post("/api/participantes", token("Monitor"), cuerpo);

        assertEquals(200, r.status());
        assertEquals(P1, r.cuerpo().get("usuarioUuid").stringValue());
        assertFalse(r.cuerpo().get("creado").booleanValue(), r.cuerpo().toString());
        assertEquals(1, usuarioRepository.count());

        Usuario unico = usuarioRepository.findById(P1).orElseThrow();
        assertEquals(EMAIL, unico.getEmail());
        assertEquals(Set.of("Asistente"), unico.getRoles());
    }

    @Test
    @DisplayName("P-02 nombre vacío del usuario existente se rellena sin cambiar el email")
    void p02bNombreVacioSeRellena() throws Exception {
        usuarioRepository.save(Usuario.builder()
                .id(P1)
                .email(EMAIL)
                .nombre("")
                .roles(Set.of("Asistente"))
                .activo(true)
                .build());

        Respuesta r = post("/api/participantes", token("Monitor"),
                participante(P1, "Nombre Nuevo", EMAIL));

        assertEquals(200, r.status());
        assertFalse(r.cuerpo().get("creado").booleanValue(), r.cuerpo().toString());
        Usuario existente = usuarioRepository.findById(P1).orElseThrow();
        assertEquals("Nombre Nuevo", existente.getNombre());
        assertEquals(EMAIL, existente.getEmail());
    }

    @Test
    @DisplayName("P-02 un usuario existente nunca cambia su email")
    void p02cEmailExistenteNoCambia() throws Exception {
        usuarioRepository.save(Usuario.builder()
                .id(P1)
                .email(EMAIL)
                .nombre(NOMBRE)
                .roles(Set.of("Asistente"))
                .activo(true)
                .build());

        Respuesta r = post("/api/participantes", token("Monitor"),
                participante(P1, NOMBRE, "otro@syncpass.local"));

        assertEquals(200, r.status());
        assertFalse(r.cuerpo().get("creado").booleanValue(), r.cuerpo().toString());
        assertEquals(EMAIL, usuarioRepository.findById(P1).orElseThrow().getEmail());
    }

    // ------------------------------------------------------------------ P-03

    @Test
    @DisplayName("P-03 email que pertenece a otro UUID: 409 EMAIL_EN_USO sin crear")
    void p03EmailDeOtroUuid() throws Exception {
        usuarioRepository.save(Usuario.builder()
                .id(P1)
                .email(EMAIL)
                .nombre(NOMBRE)
                .roles(Set.of("Asistente"))
                .activo(true)
                .build());

        Respuesta r = post("/api/participantes", token("Monitor"),
                participante(P2, NOMBRE, EMAIL));

        assertEquals(409, r.status());
        assertEquals("EMAIL_EN_USO", codigo(r), r.cuerpo().toString());
        assertTrue(usuarioRepository.findById(P2).isEmpty());
        assertEquals(1, usuarioRepository.count());

        Respuesta mayusculas = post("/api/participantes", token("Monitor"),
                participante(P3, NOMBRE, EMAIL.toUpperCase()));

        assertEquals(409, mayusculas.status(), mayusculas.cuerpo().toString());
        assertEquals("EMAIL_EN_USO", codigo(mayusculas), mayusculas.cuerpo().toString());
        assertTrue(usuarioRepository.findById(P3).isEmpty());
    }

    // ------------------------------------------------------------------ P-04

    @Test
    @DisplayName("P-04 evento inexistente: 409 EVENTO_NO_EXISTE sin crear")
    void p04EventoInexistente() throws Exception {
        Respuesta r = post("/api/participantes", token("Monitor"),
                participante(P1, NOMBRE, EMAIL, 999_999L));

        assertEquals(409, r.status());
        assertEquals("EVENTO_NO_EXISTE", codigo(r), r.cuerpo().toString());
        assertTrue(r.cuerpo().get("message").stringValue().contains("999999"), r.cuerpo().toString());
        assertTrue(usuarioRepository.findById(P1).isEmpty());
        assertEquals(0, usuarioRepository.count());
    }

    // ------------------------------------------------------------------ P-05

    @Test
    @DisplayName("P-05 sin token 401; rol no permitido y token sin roles 403; no crea nada")
    void p05SinTokenNiRolNoAutorizado() throws Exception {
        Respuesta sinToken = post("/api/participantes", null, participante(P1, NOMBRE, EMAIL));
        assertEquals(401, sinToken.status());

        Respuesta rolAjeno = post("/api/participantes", token("Invitado"), participante(P1, NOMBRE, EMAIL));
        assertEquals(403, rolAjeno.status());

        Respuesta sinRoles = post("/api/participantes", token(), participante(P1, NOMBRE, EMAIL));
        assertEquals(403, sinRoles.status());

        assertEquals(0, usuarioRepository.count());
    }

    @Test
    @DisplayName("P-05 Coordinador, Monitor y Asistente pueden provisionar")
    void p05LosTresRolesPuedenProvisionar() throws Exception {
        String[][] casos = {
                { "Coordinador", P1 },
                { "Monitor", P2 },
                { "Asistente", P3 }
        };

        for (String[] caso : casos) {
            Respuesta r = post("/api/participantes", token(caso[0]),
                    participante(caso[1], NOMBRE, null));

            assertEquals(200, r.status(), caso[0] + ": " + r.cuerpo());
            assertTrue(r.cuerpo().get("creado").booleanValue(), caso[0] + ": " + r.cuerpo());
        }

        assertEquals(3, usuarioRepository.count());
    }

    // ------------------------------------------------------------------ P-06

    @Test
    @DisplayName("P-06 UUID inválido o vacío: 400 sin crear")
    void p06UuidInvalido() throws Exception {
        String[] invalidos = { "no-es-un-uuid", "123", "", "11111111-1111-4111-8111", null };

        for (String uuid : invalidos) {
            Respuesta r = post("/api/participantes", token("Monitor"),
                    participante(uuid, NOMBRE, EMAIL));

            assertEquals(400, r.status(), "usuarioUuid=" + uuid);
            assertTrue(r.cuerpo().get("message").stringValue().contains("usuarioUuid"),
                    "usuarioUuid=" + uuid + " → " + r.cuerpo());
            assertFalse(r.cuerpo().get("success").booleanValue(), r.cuerpo().toString());
        }

        assertEquals(0, usuarioRepository.count());
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> participante(String uuid, String nombre, String email) {
        return participante(uuid, nombre, email, eventoId);
    }

    private Map<String, Object> participante(String uuid, String nombre, String email, Long evento) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("usuarioUuid", uuid);
        dto.put("eventoId", evento);
        dto.put("nombre", nombre);
        dto.put("email", email);
        return dto;
    }

    private String codigo(Respuesta respuesta) {
        assertNotNull(respuesta.cuerpo(), "respuesta sin cuerpo");
        JsonNode data = respuesta.cuerpo().get("data");
        assertNotNull(data, "respuesta sin data: " + respuesta.cuerpo());
        JsonNode codigo = data.get("codigo");
        assertNotNull(codigo, "respuesta sin data.codigo: " + respuesta.cuerpo());
        return codigo.stringValue();
    }

    private String json(Object valor) {
        return mapper.writeValueAsString(valor);
    }

    private Respuesta post(String ruta, String token, Object cuerpo) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json(cuerpo)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> respuesta = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
