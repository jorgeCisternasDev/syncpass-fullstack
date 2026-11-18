package cl.duoc.syncpass.sync.service;

import cl.duoc.syncpass.auth.repository.UsuarioRepository;
import cl.duoc.syncpass.common.LoteRechazoException;
import cl.duoc.syncpass.sync.dto.LoteError;
import cl.duoc.syncpass.sync.dto.LoteRequest;
import cl.duoc.syncpass.sync.dto.LoteResponse;
import cl.duoc.syncpass.sync.model.Asistencia;
import cl.duoc.syncpass.sync.model.AsistenciaId;
import cl.duoc.syncpass.sync.model.Encuesta;
import cl.duoc.syncpass.sync.model.EstadoAsistencia;
import cl.duoc.syncpass.sync.model.Evento;
import cl.duoc.syncpass.sync.repository.AsistenciaRepository;
import cl.duoc.syncpass.sync.repository.EncuestaRepository;
import cl.duoc.syncpass.sync.repository.EventoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Aplica un lote offline según el contrato v2.
 * <p>
 * Decisiones de implementación:
 * <ul>
 *   <li><b>Orden:</b> primero todas las asistencias y después todas las encuestas, porque una
 *       encuesta del mismo lote puede depender de una presencia que viaja en ese lote (2.5).</li>
 *   <li><b>Atomicidad:</b> cualquier rechazo lanza {@link LoteRechazoException} y la transacción
 *       completa hace rollback: no queda ninguna fila aplicada (5.4).</li>
 *   <li><b>Cupo:</b> se bloquean las filas de {@code eventos} con {@code SELECT ... FOR UPDATE}
 *       en orden ascendente de id antes de contar, y se cuentan filas de {@code asistencias} del
 *       evento en cualquier estado. Así dos lotes concurrentes no pueden superar el cupo y dos
 *       lotes con varios eventos no se bloquean en cruz (4.4).</li>
 *   <li><b>Idempotencia:</b> la clave sigue siendo {@code (eventoId, usuarioId)}; el UUID no
 *       participa de la clave y solo se rellena si estaba nulo (1.7, 2.4).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LoteService {

    private static final String TIPO_ASISTENCIA = "ASISTENCIA";
    private static final String TIPO_ENCUESTA = "ENCUESTA";

    private static final String EVENTO_NO_EXISTE = "EVENTO_NO_EXISTE";
    private static final String USUARIO_NO_EXISTE = "USUARIO_NO_EXISTE";
    private static final String CUPO_LLENO = "CUPO_LLENO";
    private static final String SIN_PRESENCIA = "SIN_PRESENCIA";
    private static final String CONFLICTO_IDENTIDAD = "CONFLICTO_IDENTIDAD";
    private static final String ESTADO_INVALIDO = "ESTADO_INVALIDO";

    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public LoteResponse procesar(LoteRequest lote) {
        List<LoteRequest.AsistenciaDto> asistencias = listaSegura(lote.getAsistencias());
        List<LoteRequest.EncuestaDto> encuestas = listaSegura(lote.getEncuestas());

        validarEstados(asistencias);

        Map<Long, Evento> eventos = bloquearEventos(asistencias, encuestas);
        validarUuids(asistencias, encuestas);

        Map<Long, Long> ocupados = new HashMap<>();
        eventos.forEach((eventoId, evento) ->
                ocupados.put(eventoId, asistenciaRepository.contarPorEvento(eventoId)));

        Contadores contadores = new Contadores();

        for (int i = 0; i < asistencias.size(); i++) {
            aplicarAsistencia(i, asistencias.get(i), eventos, ocupados, contadores);
        }
        for (int i = 0; i < encuestas.size(); i++) {
            aplicarEncuesta(i, encuestas.get(i), contadores);
        }

        return new LoteResponse(
                contadores.nuevos + contadores.actualizados + contadores.duplicados,
                contadores.nuevos,
                contadores.actualizados,
                contadores.duplicados,
                contadores.atribuidos,
                0,
                List.of());
    }

    /** 400 ESTADO_INVALIDO antes de tocar la base. */
    private void validarEstados(List<LoteRequest.AsistenciaDto> asistencias) {
        List<LoteError> errores = new ArrayList<>();
        for (int i = 0; i < asistencias.size(); i++) {
            String estado = asistencias.get(i).getEstado();
            if (estado != null && parseEstado(estado) == null) {
                errores.add(new LoteError(i, TIPO_ASISTENCIA, ESTADO_INVALIDO,
                        "El estado '" + estado + "' no es válido; use INSCRITO o PRESENTE."));
            }
        }
        if (!errores.isEmpty()) {
            throw new LoteRechazoException(HttpStatus.BAD_REQUEST,
                    "El lote contiene un estado de asistencia inválido.", errores);
        }
    }

    /**
     * Bloquea los eventos referenciados, en orden ascendente de id, y devuelve el mapa id → evento.
     * Si alguno no existe, rechaza el lote completo con 409 EVENTO_NO_EXISTE.
     */
    private Map<Long, Evento> bloquearEventos(List<LoteRequest.AsistenciaDto> asistencias,
                                              List<LoteRequest.EncuestaDto> encuestas) {
        Set<Long> ids = new TreeSet<>();
        asistencias.forEach(a -> ids.add(a.getEventoId()));
        encuestas.forEach(e -> ids.add(e.getEventoId()));

        Map<Long, Evento> eventos = new LinkedHashMap<>();
        for (Long id : ids) {
            eventoRepository.findByIdForUpdate(id).ifPresent(evento -> eventos.put(id, evento));
        }

        List<LoteError> errores = new ArrayList<>();
        for (int i = 0; i < asistencias.size(); i++) {
            Long id = asistencias.get(i).getEventoId();
            if (!eventos.containsKey(id)) {
                errores.add(new LoteError(i, TIPO_ASISTENCIA, EVENTO_NO_EXISTE,
                        "El evento " + id + " no existe."));
            }
        }
        for (int i = 0; i < encuestas.size(); i++) {
            Long id = encuestas.get(i).getEventoId();
            if (!eventos.containsKey(id)) {
                errores.add(new LoteError(i, TIPO_ENCUESTA, EVENTO_NO_EXISTE,
                        "El evento " + id + " no existe."));
            }
        }
        if (!errores.isEmpty()) {
            throw LoteRechazoException.conflicto(errores);
        }
        return eventos;
    }

    /** 409 USUARIO_NO_EXISTE si el UUID opcional no está en {@code usuarios}. */
    private void validarUuids(List<LoteRequest.AsistenciaDto> asistencias,
                              List<LoteRequest.EncuestaDto> encuestas) {
        List<LoteError> errores = new ArrayList<>();
        Set<String> revisados = new HashSet<>();

        for (int i = 0; i < asistencias.size(); i++) {
            String uuid = normalizar(asistencias.get(i).getUsuarioUuid());
            if (uuid != null && revisados.add(uuid) && !usuarioRepository.existsById(uuid)) {
                errores.add(new LoteError(i, TIPO_ASISTENCIA, USUARIO_NO_EXISTE,
                        "El usuario " + uuid + " no existe."));
            }
        }
        for (int i = 0; i < encuestas.size(); i++) {
            String uuid = normalizar(encuestas.get(i).getUsuarioUuid());
            if (uuid != null && revisados.add(uuid) && !usuarioRepository.existsById(uuid)) {
                errores.add(new LoteError(i, TIPO_ENCUESTA, USUARIO_NO_EXISTE,
                        "El usuario " + uuid + " no existe."));
            }
        }
        if (!errores.isEmpty()) {
            throw LoteRechazoException.conflicto(errores);
        }
    }

    private void aplicarAsistencia(int indice,
                                   LoteRequest.AsistenciaDto dto,
                                   Map<Long, Evento> eventos,
                                   Map<Long, Long> ocupados,
                                   Contadores contadores) {
        EstadoAsistencia estado = dto.getEstado() == null
                ? EstadoAsistencia.PRESENTE
                : parseEstado(dto.getEstado());
        String uuid = normalizar(dto.getUsuarioUuid());

        AsistenciaId id = new AsistenciaId(dto.getEventoId(), dto.getUsuarioId());
        Optional<Asistencia> existente = asistenciaRepository.findById(id);

        if (existente.isEmpty()) {
            Evento evento = eventos.get(dto.getEventoId());
            long enEvento = ocupados.getOrDefault(dto.getEventoId(), 0L);
            int cupos = evento.getCuposMax() == null ? 0 : evento.getCuposMax();
            if (cupos > 0 && enEvento >= cupos) {
                throw LoteRechazoException.conflicto(new LoteError(indice, TIPO_ASISTENCIA, CUPO_LLENO,
                        "El evento alcanzó su cupo máximo de " + cupos + " personas."));
            }

            Asistencia nueva = Asistencia.builder()
                    .id(id)
                    .usuarioUuid(uuid)
                    .estado(estado)
                    .timestamp(dto.getTimestamp())
                    .inscritoMs(null)
                    .validado(estado.validado())
                    .latitud(estado == EstadoAsistencia.PRESENTE ? dto.getLatitud() : null)
                    .longitud(estado == EstadoAsistencia.PRESENTE ? dto.getLongitud() : null)
                    .build();
            asistenciaRepository.save(nueva);

            ocupados.put(dto.getEventoId(), enEvento + 1);
            contadores.nuevos++;
            if (uuid != null) {
                contadores.atribuidos++;
            }
            return;
        }

        Asistencia fila = existente.get();

        // Conflicto de identidad: nunca se sobrescribe un UUID distinto.
        if (fila.getUsuarioUuid() != null && uuid != null && !fila.getUsuarioUuid().equals(uuid)) {
            throw LoteRechazoException.conflicto(new LoteError(indice, TIPO_ASISTENCIA, CONFLICTO_IDENTIDAD,
                    "La asistencia ya está atribuida a otro usuario."));
        }

        // Relleno de atribución: no cambia estado, timestamp ni inscrito_ms.
        boolean atribuido = false;
        if (fila.getUsuarioUuid() == null && uuid != null) {
            fila.setUsuarioUuid(uuid);
            atribuido = true;
        }

        if (fila.getEstado() == EstadoAsistencia.PRESENTE || estado == EstadoAsistencia.INSCRITO) {
            // Duplicado o transición inversa: el estado más avanzado gana y no se escribe nada.
            contadores.duplicados++;
        } else {
            // INSCRITO -> PRESENTE: misma fila; se conserva el momento de inscripción.
            fila.setInscritoMs(fila.getTimestamp());
            fila.setEstado(EstadoAsistencia.PRESENTE);
            fila.setTimestamp(dto.getTimestamp());
            fila.setValidado(true);
            fila.setLatitud(dto.getLatitud());
            fila.setLongitud(dto.getLongitud());
            contadores.actualizados++;
        }

        if (atribuido) {
            contadores.atribuidos++;
        }
    }

    private void aplicarEncuesta(int indice,
                                 LoteRequest.EncuestaDto dto,
                                 Contadores contadores) {
        AsistenciaId clave = new AsistenciaId(dto.getEventoId(), dto.getUsuarioId());
        Optional<Asistencia> asistencia = asistenciaRepository.findById(clave);
        if (asistencia.isEmpty() || asistencia.get().getEstado() != EstadoAsistencia.PRESENTE) {
            // No se distingue "no existe" de "está INSCRITO": evita filtrar la inscripción.
            throw LoteRechazoException.conflicto(new LoteError(indice, TIPO_ENCUESTA, SIN_PRESENCIA,
                    "Solo puede responder la encuesta quien figura como PRESENTE en el evento."));
        }

        String uuid = normalizar(dto.getUsuarioUuid());
        Optional<Encuesta> existente = encuestaRepository
                .findByEventoIdAndUsuarioId(dto.getEventoId(), dto.getUsuarioId());

        if (existente.isPresent()) {
            Encuesta fila = existente.get();
            if (fila.getUsuarioUuid() != null && uuid != null && !fila.getUsuarioUuid().equals(uuid)) {
                throw LoteRechazoException.conflicto(new LoteError(indice, TIPO_ENCUESTA, CONFLICTO_IDENTIDAD,
                        "La encuesta ya está atribuida a otro usuario."));
            }

            boolean atribuido = false;
            if (fila.getUsuarioUuid() == null && uuid != null) {
                fila.setUsuarioUuid(uuid);
                atribuido = true;
            }

            // D-10 (a): la primera encuesta gana; un dato viejo no pisa uno nuevo.
            contadores.duplicados++;
            if (atribuido) {
                contadores.atribuidos++;
            }
            return;
        }

        Encuesta nueva = Encuesta.builder()
                .eventoId(dto.getEventoId())
                .usuarioId(dto.getUsuarioId())
                .usuarioUuid(uuid)
                .satisfaccion(dto.getSatisfaccion())
                .utilidad(dto.getUtilidad())
                .comentario(dto.getComentario())
                .fecha(dto.getFecha())
                .build();
        encuestaRepository.save(nueva);

        contadores.nuevos++;
        if (uuid != null) {
            contadores.atribuidos++;
        }
    }

    private EstadoAsistencia parseEstado(String valor) {
        String limpio = valor.trim();
        for (EstadoAsistencia estado : EstadoAsistencia.values()) {
            if (estado.name().equals(limpio)) {
                return estado;
            }
        }
        return null;
    }

    private String normalizar(String uuid) {
        if (uuid == null) {
            return null;
        }
        String limpio = uuid.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static <T> List<T> listaSegura(List<T> lista) {
        return lista == null ? List.of() : lista;
    }

    private static final class Contadores {
        private int nuevos;
        private int actualizados;
        private int duplicados;
        private int atribuidos;
    }
}
