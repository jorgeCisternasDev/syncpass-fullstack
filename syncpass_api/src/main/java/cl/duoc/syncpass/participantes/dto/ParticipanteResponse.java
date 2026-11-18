package cl.duoc.syncpass.participantes.dto;

/**
 * Respuesta de {@code POST /api/participantes} (H3-DISENO 2.2), sin envoltorio.
 *
 * @param usuarioUuid UUID canónico del participante (mismo valor en un reintento)
 * @param creado      {@code true} si el usuario se creó en esta petición; {@code false} si ya existía
 */
public record ParticipanteResponse(String usuarioUuid, boolean creado) {
}
