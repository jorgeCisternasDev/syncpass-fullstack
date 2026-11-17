package cl.duoc.syncpass.sync;

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-key-for-integration-32b")
class SyncSecurityIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("POST /api/sync/lote sin Bearer devuelve 401")
    void syncSinTokenDevuelve401() throws Exception {
        HttpResponse<String> response = post("/api/sync/lote", null, """
                {"asistencias":[],"encuestas":[]}
                """);

        assertEquals(401, response.statusCode());
    }

    @Test
    @DisplayName("POST /api/sync/lote con rol no reconocido devuelve 403")
    void syncConRolNoReconocidoDevuelve403() throws Exception {
        HttpResponse<String> response = post("/api/sync/lote", token("Invitado"), """
                {"asistencias":[],"encuestas":[]}
                """);

        assertEquals(403, response.statusCode());
    }

    @Test
    @DisplayName("POST /api/sync/lote con Asistente autenticado responde el contrato plano")
    void syncConAsistenteAcepta() throws Exception {
        HttpResponse<String> response = post("/api/sync/lote", token("Asistente"), """
                {"asistencias":[],"encuestas":[]}
                """);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"aceptados\":0"), response.body());
    }

    @Test
    @DisplayName("El catálogo se lee con rol Asistente y se escribe solo con Coordinador")
    void catalogoRespetaRoles() throws Exception {
        HttpResponse<String> listado = get("/api/proyectos", token("Asistente"));
        assertEquals(200, listado.statusCode());
        assertTrue(listado.body().trim().startsWith("["), listado.body());

        String proyecto = """
                {
                  "titulo": "Proyecto de prueba",
                  "descripcion": "Proyecto ficticio para prueba de integración",
                  "areaTematica": "Pruebas",
                  "unidadVecinal": "Sin datos reales",
                  "fechaInicio": 1767225600000,
                  "fechaTermino": 1798761599000,
                  "estado": "EN_CURSO"
                }
                """;

        HttpResponse<String> denegado = post("/api/proyectos", token("Asistente"), proyecto);
        assertEquals(403, denegado.statusCode());

        HttpResponse<String> creado = post("/api/proyectos", token("Coordinador"), proyecto);
        assertEquals(201, creado.statusCode());
        assertTrue(creado.body().contains("\"titulo\":\"Proyecto de prueba\""), creado.body());
        assertTrue(!creado.body().contains("\"success\""), creado.body());
    }

    @Test
    @DisplayName("Evento: crear, actualizar, listar y eliminar sin envoltorio")
    void eventoCrudCompleto() throws Exception {
        String proyecto = """
                {
                  "titulo": "Proyecto eventos",
                  "descripcion": "Proyecto ficticio para eventos",
                  "areaTematica": "Pruebas",
                  "unidadVecinal": "Sin datos reales",
                  "fechaInicio": 1767225600000,
                  "fechaTermino": 1798761599000,
                  "estado": "EN_CURSO"
                }
                """;
        HttpResponse<String> proyectoCreado = post("/api/proyectos", token("Coordinador"), proyecto);
        assertEquals(201, proyectoCreado.statusCode());
        long proyectoId = idFrom(proyectoCreado.body(), "proyectoId");

        String evento = """
                {
                  "proyectoId": %d,
                  "nombre": "Taller de prueba",
                  "fechaHora": 1780329600000,
                  "cuposMax": 20,
                  "recinto": "Sede de prueba",
                  "latitud": -33.05,
                  "longitud": -71.61,
                  "radioMetros": 80
                }
                """.formatted(proyectoId);

        HttpResponse<String> eventoCreado = post("/api/eventos", token("Coordinador"), evento);
        assertEquals(201, eventoCreado.statusCode());
        assertTrue(eventoCreado.body().contains("\"nombre\":\"Taller de prueba\""), eventoCreado.body());
        assertTrue(!eventoCreado.body().contains("\"success\""), eventoCreado.body());
        long eventoId = idFrom(eventoCreado.body(), "eventoId");

        HttpResponse<String> listado = get("/api/proyectos/" + proyectoId + "/eventos", token("Monitor"));
        assertEquals(200, listado.statusCode());
        assertTrue(listado.body().trim().startsWith("["), listado.body());

        String eventoActualizado = evento.replace("Taller de prueba", "Taller actualizado");
        HttpResponse<String> actualizado = put("/api/eventos/" + eventoId, token("Coordinador"), eventoActualizado);
        assertEquals(200, actualizado.statusCode());
        assertTrue(actualizado.body().contains("\"nombre\":\"Taller actualizado\""), actualizado.body());

        HttpResponse<String> eventoEliminado = delete("/api/eventos/" + eventoId, token("Coordinador"));
        assertEquals(204, eventoEliminado.statusCode());

        HttpResponse<String> proyectoEliminado = delete("/api/proyectos/" + proyectoId, token("Coordinador"));
        assertEquals(204, proyectoEliminado.statusCode());
    }

    private long idFrom(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(body);
        assertTrue(matcher.find(), body);
        return Long.parseLong(matcher.group(1));
    }

    private String token(String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of(roles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .DELETE();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
