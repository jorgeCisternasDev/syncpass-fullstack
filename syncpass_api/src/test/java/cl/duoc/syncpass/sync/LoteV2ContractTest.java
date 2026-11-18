package cl.duoc.syncpass.sync;

import cl.duoc.syncpass.auth.model.Usuario;
import cl.duoc.syncpass.auth.repository.UsuarioRepository;
import cl.duoc.syncpass.sync.model.Asistencia;
import cl.duoc.syncpass.sync.model.AsistenciaId;
import cl.duoc.syncpass.sync.model.Encuesta;
import cl.duoc.syncpass.sync.model.EstadoAsistencia;
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
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de contrato v2 (sección 7, T-01 a T-36) contra la API real con H2 y datos ficticios.
 * Cada caso prepara su propio fixture y no toca datos de otros eventos.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-key-for-integration-32b")
class LoteV2ContractTest {

    private static final String U1 = "11111111-1111-4111-8111-111111111111";
    private static final String U2 = "22222222-2222-4222-8222-222222222222";
    private static final String U3 = "33333333-3333-4333-8333-333333333333";
    private static final String UUID_INEXISTENTE = "99999999-9999-4999-8999-999999999999";

    private static final long TS_INSCRITO = 1_794_000_000_000L;
    private static final long TS_PRESENTE = 1_794_500_000_000L;
    private static final long TS_ENCUESTA = 1_794_500_600_000L;

    private static final String MENSAJE_FORMATO = "El lote no tiene el formato esperado.";

    @Value("${local.server.port}")
    private int port;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AsistenciaRepository asistenciaRepository;

    @Autowired
    private EncuestaRepository encuestaRepository;

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private Long proyectoId;
    private Long eAbierto;
    private Long eLleno;
    private Long eCupo1;
    private Long eInscrito;
    private Long eLlenoInscrito;

    @BeforeEach
    void preparar() {
        encuestaRepository.deleteAll();
        asistenciaRepository.deleteAll();
        eventoRepository.deleteAll();
        proyectoRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario(U1, "u1@ficticio.cl", "Uno Ficticio", "Asistente");
        usuario(U2, "u2@ficticio.cl", "Dos Ficticio", "Monitor");
        usuario(U3, "u3@ficticio.cl", "Tres Ficticio", "Asistente");

        Proyecto proyecto = proyectoRepository.save(Proyecto.builder()
                .titulo("Proyecto ficticio de contrato v2")
                .descripcion("Datos ficticios para pruebas automatizadas")
                .areaTematica("Pruebas")
                .unidadVecinal("Sin datos reales")
                .fechaInicio(1_700_000_000_000L)
                .fechaTermino(1_900_000_000_000L)
                .estado("EN_CURSO")
                .build());
        proyectoId = proyecto.getId();

        eAbierto = evento("E-ABIERTO", 30);
        eLleno = evento("E-LLENO", 2);
        eCupo1 = evento("E-CUPO-1", 2);
        eInscrito = evento("E-INSCRITO", 30);
        eLlenoInscrito = evento("E-LLENO-INSCRITO", 2);

        fila(eLleno, 1L, EstadoAsistencia.PRESENTE, null, TS_INSCRITO);
        fila(eLleno, 2L, EstadoAsistencia.PRESENTE, null, TS_INSCRITO);
        fila(eCupo1, 1L, EstadoAsistencia.PRESENTE, null, TS_INSCRITO);
        fila(eInscrito, 1L, EstadoAsistencia.INSCRITO, null, TS_INSCRITO);
        fila(eLlenoInscrito, 1L, EstadoAsistencia.INSCRITO, null, TS_INSCRITO);
        fila(eLlenoInscrito, 3L, EstadoAsistencia.PRESENTE, null, TS_INSCRITO);
    }

    // ---------------------------------------------------------------- seguridad

    @Test
    @DisplayName("T-01 sin token devuelve 401 con WWW-Authenticate Bearer")
    void t01SinToken() throws Exception {
        Respuesta r = postLote(null, lote(List.of(), List.of()));

        assertEquals(401, r.status());
        assertTrue(r.headers().firstValue("WWW-Authenticate").orElse("").contains("Bearer"));        assertEquals(0, encuestaRepository.count());
    }

    @Test
    @DisplayName("T-02 token sin rol devuelve 403 y no escribe")
    void t02TokenSinRol() throws Exception {
        Respuesta r = postLote(token(), lote(List.of(asistencia(eAbierto, 1L, 1L)), List.of()));

        assertEquals(403, r.status());
        assertNull(fila(eAbierto, 1L));
    }

    @Test
    @DisplayName("T-03 rol no autorizado (Administrador) devuelve 403")
    void t03RolNoAutorizado() throws Exception {
        Respuesta r = postLote(token("Administrador"), lote(List.of(asistencia(eAbierto, 1L, 1L)), List.of()));

        assertEquals(403, r.status());
        assertNull(fila(eAbierto, 1L));
    }

    @Test
    @DisplayName("T-04 token expirado devuelve 401")
    void t04TokenExpirado() throws Exception {
        Respuesta r = postLote(tokenExpirado(), lote(List.of(asistencia(eAbierto, 1L, 1L)), List.of()));

        assertEquals(401, r.status());
        assertNull(fila(eAbierto, 1L));
    }

    // ---------------------------------------------------------------- lote feliz y duplicados

    @Test
    @DisplayName("T-05 lote válido nuevo: asistencia PRESENTE + encuesta")
    void t05LoteNuevo() throws Exception {
        String cuerpo = lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "estado", "PRESENTE")),
                List.of(encuesta(eAbierto, 1L, 5, 4, TS_ENCUESTA)));

        Respuesta r = postLote(token("Monitor"), cuerpo);

        assertEquals(200, r.status());
        assertEquals(2, entero(r, "aceptados"));
        assertEquals(2, entero(r, "nuevos"));
        assertEquals(0, entero(r, "actualizados"));
        assertEquals(0, entero(r, "duplicados"));
        assertEquals(0, entero(r, "atribuidos"));
        assertEquals(0, entero(r, "rechazados"));
        assertTrue(r.cuerpo().get("errores").isArray());
        assertEquals(0, r.cuerpo().get("errores").size());

        Asistencia asistencia = fila(eAbierto, 1L);
        assertNotNull(asistencia);
        assertEquals(EstadoAsistencia.PRESENTE, asistencia.getEstado());
        assertTrue(asistencia.isValidado());
        assertEquals(TS_PRESENTE, asistencia.getTimestamp());
        assertEquals(1, encuestaRepository.count());
    }

    @Test
    @DisplayName("T-06 reenvío exacto: duplicados=2 y base idéntica")
    void t06ReenvioExacto() throws Exception {
        String cuerpo = lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "estado", "PRESENTE")),
                List.of(encuesta(eAbierto, 1L, 5, 4, TS_ENCUESTA)));

        assertEquals(200, postLote(token("Monitor"), cuerpo).status());

        Asistencia antes = fila(eAbierto, 1L);
        Encuesta encuestaAntes = encuestaRepository.findByEventoIdAndUsuarioId(eAbierto, 1L).orElseThrow();
        long timestampAntes = antes.getTimestamp();
        Integer satisfaccionAntes = encuestaAntes.getSatisfaccion();
        Long fechaAntes = encuestaAntes.getFecha();

        Respuesta r = postLote(token("Monitor"), cuerpo);

        assertEquals(200, r.status());
        assertEquals(2, entero(r, "aceptados"));
        assertEquals(0, entero(r, "nuevos"));
        assertEquals(0, entero(r, "actualizados"));
        assertEquals(2, entero(r, "duplicados"));

        Asistencia despues = fila(eAbierto, 1L);
        assertEquals(timestampAntes, despues.getTimestamp());
        assertEquals(EstadoAsistencia.PRESENTE, despues.getEstado());
        assertNull(despues.getInscritoMs());

        Encuesta encuestaDespues = encuestaRepository.findByEventoIdAndUsuarioId(eAbierto, 1L).orElseThrow();
        assertEquals(satisfaccionAntes, encuestaDespues.getSatisfaccion());
        assertEquals(fechaAntes, encuestaDespues.getFecha());
        assertEquals(1, encuestaRepository.count());
    }

    @Test
    @DisplayName("T-07 INSCRITO a PRESENTE conserva inscrito_ms y actualiza timestamp")
    void t07Transicion() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eInscrito, 1L, TS_PRESENTE), "estado", "PRESENTE")),
                List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "aceptados"));
        assertEquals(1, entero(r, "actualizados"));
        assertEquals(0, entero(r, "nuevos"));

        Asistencia fila = fila(eInscrito, 1L);
        assertEquals(EstadoAsistencia.PRESENTE, fila.getEstado());
        assertEquals(TS_INSCRITO, fila.getInscritoMs());
        assertEquals(TS_PRESENTE, fila.getTimestamp());
        assertTrue(fila.isValidado());
        assertEquals(1, asistenciaRepository.contarPorEvento(eInscrito));
    }

    @Test
    @DisplayName("T-08 transición inversa: gana PRESENTE y se cuenta duplicado")
    void t08TransicionInversa() throws Exception {
        filaActualizar(eInscrito, 1L, EstadoAsistencia.PRESENTE, null, TS_PRESENTE);

        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eInscrito, 1L, TS_PRESENTE + 1_000), "estado", "INSCRITO")),
                List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "aceptados"));
        assertEquals(0, entero(r, "actualizados"));
        assertEquals(1, entero(r, "duplicados"));

        Asistencia fila = fila(eInscrito, 1L);
        assertEquals(EstadoAsistencia.PRESENTE, fila.getEstado());
        assertEquals(TS_PRESENTE, fila.getTimestamp());
    }

    @Test
    @DisplayName("T-09 lote parcial: un evento inexistente rechaza todo y no deja filas")
    void t09LoteParcial() throws Exception {
        String cuerpo = lote(List.of(
                asistencia(eAbierto, 1L, TS_PRESENTE),
                asistencia(999_999L, 2L, TS_PRESENTE)), List.of());

        Respuesta r = postLote(token("Monitor"), cuerpo);

        assertEquals(409, r.status());
        JsonNode error = primerError(r);
        assertEquals("EVENTO_NO_EXISTE", error.get("codigo").stringValue());
        assertEquals(1, error.get("indice").intValue());
        assertEquals("ASISTENCIA", error.get("tipo").stringValue());
        assertTrue(error.get("mensaje").stringValue().contains("999999"));

        assertNull(fila(eAbierto, 1L));
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
        assertFalse(r.cuerpo().get("success").booleanValue());
    }

    @Test
    @DisplayName("T-10 timestamp antiguo se guarda tal cual, sin reemplazarlo por now")
    void t10TimestampAntiguo() throws Exception {
        long haceNoventaDias = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000;

        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eAbierto, 1L, haceNoventaDias)), List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "nuevos"));
        assertEquals(haceNoventaDias, fila(eAbierto, 1L).getTimestamp());
    }

    @Test
    @DisplayName("T-11 timestamp 0, null o negativo devuelve 400")
    void t11TimestampInvalido() throws Exception {
        for (Long timestamp : new Long[] { 0L, null, -5L }) {
            Respuesta r = postLote(token("Monitor"), lote(
                    List.of(asistencia(eAbierto, 1L, timestamp)), List.of()));

            assertEquals(400, r.status(), "timestamp=" + timestamp);
            assertTrue(r.cuerpo().get("message").stringValue().contains("timestamp"), r.cuerpo().toString());
            assertNull(fila(eAbierto, 1L));
        }
    }

    // ---------------------------------------------------------------- cupos

    @Test
    @DisplayName("T-12 cupo lleno: fila nueva rechazada con el N del evento")
    void t12CupoLleno() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eLleno, 3L, TS_PRESENTE)), List.of()));

        assertEquals(409, r.status());
        JsonNode error = primerError(r);
        assertEquals("CUPO_LLENO", error.get("codigo").stringValue());
        assertTrue(error.get("mensaje").stringValue().contains("2"), error.toString());
        assertEquals(2, asistenciaRepository.contarPorEvento(eLleno));
        assertNull(fila(eLleno, 3L));
    }

    @Test
    @DisplayName("T-13 transición con cupo lleno se acepta: no consume cupo nuevo")
    void t13TransicionConCupoLleno() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eLlenoInscrito, 1L, TS_PRESENTE), "estado", "PRESENTE")),
                List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "actualizados"));
        assertEquals(EstadoAsistencia.PRESENTE, fila(eLlenoInscrito, 1L).getEstado());
        assertEquals(2, asistenciaRepository.contarPorEvento(eLlenoInscrito));
    }

    @Test
    @DisplayName("T-14 cupo justo: la última fila permitida entra")
    void t14CupoJusto() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eCupo1, 3L, TS_PRESENTE)), List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "nuevos"));
        assertEquals(2, asistenciaRepository.contarPorEvento(eCupo1));
    }

    @Test
    @DisplayName("H-01 concurrencia de cupo: dos lotes simultáneos con cuposMax=1 dejan una sola fila")
    void h01ConcurrenciaDeCupo() throws Exception {
        String tokenMonitor = token("Monitor");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 1; ronda <= 3; ronda++) {
                Long evento = evento("E-CONCURRENCIA-" + ronda, 1);
                CountDownLatch listos = new CountDownLatch(2);
                CountDownLatch salida = new CountDownLatch(1);

                Future<Respuesta> primero = pool.submit(loteConcurrente(
                        tokenMonitor, evento, 1L, listos, salida));
                Future<Respuesta> segundo = pool.submit(loteConcurrente(
                        tokenMonitor, evento, 2L, listos, salida));

                assertTrue(listos.await(10, TimeUnit.SECONDS), "los dos hilos no quedaron listos");
                salida.countDown();

                Respuesta r1 = primero.get(30, TimeUnit.SECONDS);
                Respuesta r2 = segundo.get(30, TimeUnit.SECONDS);
                List<Respuesta> respuestas = List.of(r1, r2);

                long exitos = respuestas.stream().filter(respuesta -> respuesta.status() == 200).count();
                long rechazos = respuestas.stream().filter(respuesta -> respuesta.status() == 409).count();

                assertEquals(1, exitos, "ronda " + ronda + " estados=" + estados(respuestas));
                assertEquals(1, rechazos, "ronda " + ronda + " estados=" + estados(respuestas));

                Respuesta rechazo = respuestas.stream()
                        .filter(respuesta -> respuesta.status() == 409)
                        .findFirst()
                        .orElseThrow();
                assertEquals("CUPO_LLENO", primerError(rechazo).get("codigo").stringValue());
                assertEquals(1, asistenciaRepository.contarPorEvento(evento), "ronda " + ronda);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Respuesta> loteConcurrente(String tokenMonitor,
                                                Long evento,
                                                Long usuarioId,
                                                CountDownLatch listos,
                                                CountDownLatch salida) {
        return () -> {
            listos.countDown();
            if (!salida.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("no llegó la señal de salida");
            }
            return postLote(tokenMonitor, lote(
                    List.of(asistencia(evento, usuarioId, TS_PRESENTE)), List.of()));
        };
    }

    private String estados(List<Respuesta> respuestas) {
        return respuestas.stream()
                .map(respuesta -> String.valueOf(respuesta.status()))
                .toList()
                .toString();
    }

    // ---------------------------------------------------------------- presencia y encuestas

    @Test
    @DisplayName("T-15 encuesta sin presencia devuelve 409 SIN_PRESENCIA")
    void t15EncuestaSinPresencia() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(), List.of(encuesta(eAbierto, 2L, 4, 4, TS_ENCUESTA))));

        assertEquals(409, r.status());
        assertEquals("SIN_PRESENCIA", primerError(r).get("codigo").stringValue());
        assertEquals(0, encuestaRepository.count());
    }

    @Test
    @DisplayName("T-16 encuesta con INSCRITO pero sin PRESENTE devuelve 409")
    void t16EncuestaConInscripcionSinPresencia() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(), List.of(encuesta(eInscrito, 1L, 4, 4, TS_ENCUESTA))));

        assertEquals(409, r.status());
        assertEquals("SIN_PRESENCIA", primerError(r).get("codigo").stringValue());
        assertEquals(0, encuestaRepository.count());
        assertEquals(EstadoAsistencia.INSCRITO, fila(eInscrito, 1L).getEstado());
    }

    @Test
    @DisplayName("T-17 presencia y encuesta en el mismo lote")
    void t17PresenciaEnElMismoLote() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eAbierto, 1L, TS_PRESENTE)),
                List.of(encuesta(eAbierto, 1L, 5, 5, TS_ENCUESTA))));

        assertEquals(200, r.status());
        assertEquals(2, entero(r, "aceptados"));
        assertEquals(2, entero(r, "nuevos"));
        assertEquals(1, asistenciaRepository.contarPorEvento(eAbierto));
        assertEquals(1, encuestaRepository.count());
    }

    @Test
    @DisplayName("T-18 reenvío de T-17: duplicados=2 y estado completo idéntico")
    void t18EncuestaDuplicada() throws Exception {
        String cuerpo = lote(
                List.of(asistencia(eAbierto, 1L, TS_PRESENTE)),
                List.of(encuesta(eAbierto, 1L, 5, 5, TS_ENCUESTA)));

        assertEquals(200, postLote(token("Monitor"), cuerpo).status());

        Asistencia asistenciaAntes = fila(eAbierto, 1L);
        Encuesta encuestaAntes = encuestaRepository
                .findByEventoIdAndUsuarioId(eAbierto, 1L)
                .orElseThrow();
        Long idEncuestaAntes = encuestaAntes.getId();
        long timestampAntes = asistenciaAntes.getTimestamp();

        Respuesta r = postLote(token("Monitor"), cuerpo);

        assertEquals(200, r.status());
        assertEquals(2, entero(r, "duplicados"));
        assertEquals(0, entero(r, "nuevos"));
        assertEquals(0, entero(r, "actualizados"));

        Asistencia asistenciaDespues = fila(eAbierto, 1L);
        assertEquals(timestampAntes, asistenciaDespues.getTimestamp());
        assertEquals(EstadoAsistencia.PRESENTE, asistenciaDespues.getEstado());
        assertEquals(asistenciaAntes.isValidado(), asistenciaDespues.isValidado());
        assertNull(asistenciaDespues.getInscritoMs());
        assertEquals(asistenciaAntes.getUsuarioUuid(), asistenciaDespues.getUsuarioUuid());
        assertEquals(asistenciaAntes.getLatitud(), asistenciaDespues.getLatitud());
        assertEquals(asistenciaAntes.getLongitud(), asistenciaDespues.getLongitud());

        Encuesta encuestaDespues = encuestaRepository
                .findByEventoIdAndUsuarioId(eAbierto, 1L)
                .orElseThrow();
        assertEquals(idEncuestaAntes, encuestaDespues.getId());
        assertEquals(encuestaAntes.getSatisfaccion(), encuestaDespues.getSatisfaccion());
        assertEquals(encuestaAntes.getUtilidad(), encuestaDespues.getUtilidad());
        assertEquals(encuestaAntes.getFecha(), encuestaDespues.getFecha());
        assertEquals(encuestaAntes.getComentario(), encuestaDespues.getComentario());

        assertEquals(1, asistenciaRepository.contarPorEvento(eAbierto));
        assertEquals(1, encuestaRepository.count());
    }

    // ---------------------------------------------------------------- validación de campos

    @Test
    @DisplayName("T-19 eventoId nulo devuelve 400")
    void t19EventoIdNulo() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(null, 1L, TS_PRESENTE)), List.of()));

        assertEquals(400, r.status());
        assertTrue(r.cuerpo().get("message").stringValue().contains("eventoId"), r.cuerpo().toString());
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
    }

    @Test
    @DisplayName("T-20 usuarioId nulo devuelve 400")
    void t20UsuarioIdNulo() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eAbierto, null, TS_PRESENTE)), List.of()));

        assertEquals(400, r.status());
        assertTrue(r.cuerpo().get("message").stringValue().contains("usuarioId"), r.cuerpo().toString());
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
    }

    @Test
    @DisplayName("T-21 usuarioId con tipo erróneo devuelve 400 de formato")
    void t21UsuarioIdTipoErroneo() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eAbierto, "abc", TS_PRESENTE)), List.of()));

        assertEquals(400, r.status());
        assertEquals(MENSAJE_FORMATO, r.cuerpo().get("message").stringValue());
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
    }

    @Test
    @DisplayName("T-22 estado PENDIENTE devuelve 400 ESTADO_INVALIDO")
    void t22EstadoInvalido() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "estado", "PENDIENTE")),
                List.of()));

        assertEquals(400, r.status());
        assertEquals("ESTADO_INVALIDO", primerError(r).get("codigo").stringValue());
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
    }

    @Test
    @DisplayName("T-23 estado ausente se interpreta PRESENTE")
    void t23EstadoAusente() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eAbierto, 1L, TS_PRESENTE)), List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "nuevos"));

        Asistencia fila = fila(eAbierto, 1L);
        assertEquals(EstadoAsistencia.PRESENTE, fila.getEstado());
        assertTrue(fila.isValidado());
    }

    @Test
    @DisplayName("T-24 campos desconocidos se ignoran")
    void t24CamposDesconocidos() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "origen", "QR")), List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "nuevos"));
        assertEquals(1, asistenciaRepository.contarPorEvento(eAbierto));
    }

    @Test
    @DisplayName("T-25 satisfacción o utilidad fuera de 1..5 devuelve 400")
    void t25EncuestaFueraDeRango() throws Exception {
        fila(eAbierto, 1L, EstadoAsistencia.PRESENTE, null, TS_PRESENTE);

        for (int valor : new int[] { 0, 6 }) {
            Respuesta r = postLote(token("Monitor"), lote(
                    List.of(), List.of(encuesta(eAbierto, 1L, valor, 4, TS_ENCUESTA))));

            assertEquals(400, r.status(), "satisfaccion=" + valor);
            assertTrue(r.cuerpo().get("message").stringValue().contains("1 y 5"), r.cuerpo().toString());
            assertEquals(0, encuestaRepository.count());
        }
    }

    @Test
    @DisplayName("T-26 comentario de 601 caracteres devuelve 400")
    void t26ComentarioLargo() throws Exception {
        fila(eAbierto, 1L, EstadoAsistencia.PRESENTE, null, TS_PRESENTE);
        String comentario = "x".repeat(601);

        Respuesta r = postLote(token("Monitor"), lote(
                List.of(), List.of(con(encuesta(eAbierto, 1L, 5, 5, TS_ENCUESTA), "comentario", comentario))));

        assertEquals(400, r.status());
        assertTrue(r.cuerpo().get("message").stringValue().contains("600"), r.cuerpo().toString());
        assertEquals(0, encuestaRepository.count());
    }

    // ---------------------------------------------------------------- duplicados y opcionales

    @Test
    @DisplayName("T-27 reenvío en evento lleno cuenta duplicado, no cupo")
    void t27EventoLlenoConDuplicado() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eLleno, 1L, TS_INSCRITO)), List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "duplicados"));
        assertEquals(0, entero(r, "nuevos"));
        assertEquals(2, asistenciaRepository.contarPorEvento(eLleno));
    }

    @Test
    @DisplayName("T-28 lote vacío responde 200 con aceptados=0")
    void t28LoteVacio() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(List.of(), List.of()));

        assertEquals(200, r.status());
        assertEquals(0, entero(r, "aceptados"));
        assertEquals(0, entero(r, "rechazados"));
    }

    @Test
    @DisplayName("T-29 cuerpo sin listas responde 200 con aceptados=0")
    void t29SinListas() throws Exception {
        Respuesta r = postLote(token("Monitor"), "{}");

        assertEquals(200, r.status());
        assertEquals(0, entero(r, "aceptados"));
    }

    @Test
    @DisplayName("T-30 usuarioUuid ausente deja la fila sin atribución")
    void t30UuidAusente() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eAbierto, 1L, TS_PRESENTE)), List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "nuevos"));
        assertEquals(0, entero(r, "atribuidos"));
        assertNull(fila(eAbierto, 1L).getUsuarioUuid());
    }

    @Test
    @DisplayName("T-31 usuarioUuid válido atribuye la fila")
    void t31UuidValido() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "usuarioUuid", U1)), List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "nuevos"));
        assertEquals(1, entero(r, "atribuidos"));
        assertEquals(U1, fila(eAbierto, 1L).getUsuarioUuid());
    }

    @Test
    @DisplayName("T-32 usuarioUuid inexistente devuelve 409 USUARIO_NO_EXISTE")
    void t32UuidInexistente() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "usuarioUuid", UUID_INEXISTENTE)),
                List.of()));

        assertEquals(409, r.status());
        assertEquals("USUARIO_NO_EXISTE", primerError(r).get("codigo").stringValue());
        assertNull(fila(eAbierto, 1L));
    }

    @Test
    @DisplayName("T-33 usuarioUuid con tipo erróneo devuelve 400 de formato")
    void t33UuidTipoErroneo() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "usuarioUuid", 123)), List.of()));

        assertEquals(400, r.status());
        assertEquals(MENSAJE_FORMATO, r.cuerpo().get("message").stringValue());
        assertNull(fila(eAbierto, 1L));
    }

    @Test
    @DisplayName("T-34 relleno de UUID en fila previa sin cambiar estado ni timestamp")
    void t34RellenoDeUuid() throws Exception {
        fila(eAbierto, 1L, EstadoAsistencia.PRESENTE, null, TS_PRESENTE);

        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "usuarioUuid", U1)), List.of()));

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "duplicados"));
        assertEquals(1, entero(r, "atribuidos"));

        Asistencia fila = fila(eAbierto, 1L);
        assertEquals(U1, fila.getUsuarioUuid());
        assertEquals(EstadoAsistencia.PRESENTE, fila.getEstado());
        assertEquals(TS_PRESENTE, fila.getTimestamp());
    }

    @Test
    @DisplayName("T-35 UUID distinto en fila existente devuelve 409 CONFLICTO_IDENTIDAD")
    void t35ConflictoDeUuid() throws Exception {
        fila(eAbierto, 1L, EstadoAsistencia.PRESENTE, U1, TS_PRESENTE);

        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "usuarioUuid", U2)), List.of()));

        assertEquals(409, r.status());
        assertEquals("CONFLICTO_IDENTIDAD", primerError(r).get("codigo").stringValue());
        assertEquals(U1, fila(eAbierto, 1L).getUsuarioUuid());
    }

    @Test
    @DisplayName("T-36 reenvío con el mismo UUID no vuelve a atribuir")
    void t36ReenvioConMismoUuid() throws Exception {
        String cuerpo = lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "usuarioUuid", U1)), List.of());

        assertEquals(200, postLote(token("Monitor"), cuerpo).status());
        Respuesta r = postLote(token("Monitor"), cuerpo);

        assertEquals(200, r.status());
        assertEquals(1, entero(r, "duplicados"));
        assertEquals(0, entero(r, "atribuidos"));
        assertEquals(U1, fila(eAbierto, 1L).getUsuarioUuid());
    }

    @Test
    @DisplayName("v2 validado se ignora: INSCRITO con validado=true se guarda false y PRESENTE al revés")
    void validadoSeDerivaDelEstado() throws Exception {
        Map<String, Object> inscrito = con(con(asistencia(eAbierto, 1L, TS_PRESENTE), "estado", "INSCRITO"), "validado", true);
        Map<String, Object> presente = con(asistencia(eAbierto, 2L, TS_PRESENTE), "validado", false);

        Respuesta r = postLote(token("Monitor"), lote(List.of(inscrito, presente), List.of()));

        assertEquals(200, r.status());
        assertEquals(2, entero(r, "nuevos"));

        Asistencia filaInscrita = fila(eAbierto, 1L);
        assertEquals(EstadoAsistencia.INSCRITO, filaInscrita.getEstado());
        assertFalse(filaInscrita.isValidado());

        Asistencia filaPresente = fila(eAbierto, 2L);
        assertEquals(EstadoAsistencia.PRESENTE, filaPresente.getEstado());
        assertTrue(filaPresente.isValidado());
    }

    @Test
    @DisplayName("v2 atomicidad: la asistencia válida no queda si una encuesta posterior se rechaza")
    void atomicidadConRechazoPosterior() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(asistencia(eAbierto, 1L, TS_PRESENTE)),
                List.of(encuesta(eAbierto, 2L, 4, 4, TS_ENCUESTA))));

        assertEquals(409, r.status());
        assertEquals("SIN_PRESENCIA", primerError(r).get("codigo").stringValue());
        assertNull(fila(eAbierto, 1L));
        assertEquals(0, encuestaRepository.count());
    }

    // ---------------------------------------------------------------- validación adicional H-04..H-09

    @Test
    @DisplayName("H-04 latitud 91 devuelve 400 sin escribir")
    void h04LatitudFueraDeRango() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "latitud", 91.0)), List.of()));

        assertEquals(400, r.status());
        assertTrue(r.cuerpo().get("message").stringValue().contains("latitud"), r.cuerpo().toString());
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
    }

    @Test
    @DisplayName("H-05 longitud 181 devuelve 400 sin escribir")
    void h05LongitudFueraDeRango() throws Exception {
        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "longitud", 181.0)), List.of()));

        assertEquals(400, r.status());
        assertTrue(r.cuerpo().get("message").stringValue().contains("longitud"), r.cuerpo().toString());
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
    }

    @Test
    @DisplayName("H-06 usuarioUuid de 65 caracteres devuelve 400 sin escribir")
    void h06UuidDemasiadoLargo() throws Exception {
        String uuid = "a".repeat(65);

        Respuesta r = postLote(token("Monitor"), lote(
                List.of(con(asistencia(eAbierto, 1L, TS_PRESENTE), "usuarioUuid", uuid)), List.of()));

        assertEquals(400, r.status());
        assertTrue(r.cuerpo().get("message").stringValue().contains("64"), r.cuerpo().toString());
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
    }

    @Test
    @DisplayName("H-07 fecha de encuesta 0 y -1 devuelve 400 sin escribir")
    void h07FechaDeEncuestaInvalida() throws Exception {
        fila(eAbierto, 1L, EstadoAsistencia.PRESENTE, null, TS_PRESENTE);

        for (long fecha : new long[] { 0L, -1L }) {
            Respuesta r = postLote(token("Monitor"), lote(
                    List.of(), List.of(encuesta(eAbierto, 1L, 4, 4, fecha))));

            assertEquals(400, r.status(), "fecha=" + fecha);
            assertTrue(r.cuerpo().get("message").stringValue().contains("fecha"), r.cuerpo().toString());
            assertEquals(0, encuestaRepository.count());
        }
    }

    @Test
    @DisplayName("H-08 eventoId 0 y -1 devuelve 400 sin escribir")
    void h08EventoIdNoPositivo() throws Exception {
        for (long eventoId : new long[] { 0L, -1L }) {
            Respuesta r = postLote(token("Monitor"), lote(
                    List.of(asistencia(eventoId, 1L, TS_PRESENTE)), List.of()));

            assertEquals(400, r.status(), "eventoId=" + eventoId);
            assertTrue(r.cuerpo().get("message").stringValue().contains("eventoId"), r.cuerpo().toString());
            assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
        }
    }

    @Test
    @DisplayName("H-09 JSON malformado devuelve 400 de formato sin escribir")
    void h09JsonMalformado() throws Exception {
        Respuesta r = postLote(token("Monitor"), "{\"asistencias\": [");

        assertEquals(400, r.status());
        assertEquals(MENSAJE_FORMATO, r.cuerpo().get("message").stringValue());
        assertEquals(0, asistenciaRepository.contarPorEvento(eAbierto));
        assertEquals(0, encuestaRepository.count());
    }

    // ---------------------------------------------------------------- cuposMax = 0 (D-12)

    @Test
    @DisplayName("D-12 cuposMax=0 se acepta al crear y no limita el lote")
    void cuposCeroSinLimite() throws Exception {
        String evento = """
                {
                  "proyectoId": %d,
                  "nombre": "Evento sin límite",
                  "fechaHora": 1794000000000,
                  "cuposMax": 0,
                  "recinto": "Recinto ficticio",
                  "radioMetros": 100
                }
                """.formatted(proyectoId);

        Respuesta creado = post("/api/eventos", token("Coordinador"), evento);

        assertEquals(201, creado.status());
        Long eventoId = creado.cuerpo().get("eventoId").longValue();

        Respuesta r = postLote(token("Monitor"), lote(List.of(
                asistencia(eventoId, 1L, TS_PRESENTE),
                asistencia(eventoId, 2L, TS_PRESENTE),
                asistencia(eventoId, 3L, TS_PRESENTE)), List.of()));

        assertEquals(200, r.status());
        assertEquals(3, entero(r, "nuevos"));
        assertEquals(3, asistenciaRepository.contarPorEvento(eventoId));
    }

    // ---------------------------------------------------------------- helpers de fixture

    private void usuario(String id, String email, String nombre, String rol) {
        usuarioRepository.save(Usuario.builder()
                .id(id)
                .email(email)
                .nombre(nombre)
                .roles(Set.of(rol))
                .activo(true)
                .build());
    }

    private Long evento(String nombre, int cuposMax) {
        Evento evento = eventoRepository.save(Evento.builder()
                .proyecto(proyectoRepository.findById(proyectoId).orElseThrow())
                .nombre(nombre)
                .fechaHora(TS_INSCRITO)
                .cuposMax(cuposMax)
                .recinto("Recinto ficticio")
                .radioMetros(100)
                .build());
        return evento.getId();
    }

    private void fila(Long eventoId, Long usuarioId, EstadoAsistencia estado, String uuid, long timestamp) {
        asistenciaRepository.save(Asistencia.builder()
                .id(new AsistenciaId(eventoId, usuarioId))
                .usuarioUuid(uuid)
                .estado(estado)
                .timestamp(timestamp)
                .validado(estado.validado())
                .build());
    }

    private void filaActualizar(Long eventoId, Long usuarioId, EstadoAsistencia estado, String uuid, long timestamp) {
        Asistencia fila = fila(eventoId, usuarioId);
        fila.setEstado(estado);
        fila.setUsuarioUuid(uuid);
        fila.setTimestamp(timestamp);
        fila.setValidado(estado.validado());
        asistenciaRepository.save(fila);
    }

    private Asistencia fila(Long eventoId, Long usuarioId) {
        return asistenciaRepository.findById(new AsistenciaId(eventoId, usuarioId)).orElse(null);
    }

    private Map<String, Object> asistencia(Object eventoId, Object usuarioId, Object timestamp) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("eventoId", eventoId);
        dto.put("usuarioId", usuarioId);
        dto.put("timestamp", timestamp);
        return dto;
    }

    private Map<String, Object> encuesta(Object eventoId, Object usuarioId, int satisfaccion, int utilidad, Object fecha) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("eventoId", eventoId);
        dto.put("usuarioId", usuarioId);
        dto.put("satisfaccion", satisfaccion);
        dto.put("utilidad", utilidad);
        dto.put("fecha", fecha);
        return dto;
    }

    private Map<String, Object> con(Map<String, Object> dto, String campo, Object valor) {
        Map<String, Object> copia = new LinkedHashMap<>(dto);
        copia.put(campo, valor);
        return copia;
    }

    private String lote(List<Map<String, Object>> asistencias, List<Map<String, Object>> encuestas) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("asistencias", asistencias);
        cuerpo.put("encuestas", encuestas);
        return json(cuerpo);
    }

    // ---------------------------------------------------------------- helpers HTTP

    private Respuesta postLote(String token, String cuerpo) throws Exception {
        return post("/api/sync/lote", token, cuerpo);
    }

    private Respuesta post(String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> respuesta = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = respuesta.body() == null || respuesta.body().isBlank()
                ? null
                : mapper.readTree(respuesta.body());
        return new Respuesta(respuesta.statusCode(), json, respuesta.headers());
    }

    private String json(Object valor) {
        return mapper.writeValueAsString(valor);
    }

    private int entero(Respuesta respuesta, String campo) {
        assertNotNull(respuesta.cuerpo(), "respuesta sin cuerpo");
        JsonNode valor = respuesta.cuerpo().get(campo);
        assertNotNull(valor, "falta el campo " + campo + " en " + respuesta.cuerpo());
        return valor.intValue();
    }

    private JsonNode primerError(Respuesta respuesta) {
        assertNotNull(respuesta.cuerpo(), "respuesta sin cuerpo");
        JsonNode errores = respuesta.cuerpo().get("data").get("errores");
        assertNotNull(errores, "respuesta sin data.errores: " + respuesta.cuerpo());
        assertFalse(errores.isEmpty(), "data.errores vacío: " + respuesta.cuerpo());
        return errores.get(0);
    }

    private String token(String... roles) {
        Instant ahora = Instant.now();
        return firmar(JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("usuario-ficticio-de-pruebas")
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(3600))
                .claim("roles", List.of(roles))
                .build());
    }

    private String tokenExpirado() {
        Instant ahora = Instant.now();
        return firmar(JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("usuario-ficticio-de-pruebas")
                .issuedAt(ahora.minusSeconds(7200))
                .expiresAt(ahora.minusSeconds(3600))
                .claim("roles", List.of("Monitor"))
                .build());
    }

    private String firmar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private record Respuesta(int status, JsonNode cuerpo, HttpHeaders headers) {
    }
}
