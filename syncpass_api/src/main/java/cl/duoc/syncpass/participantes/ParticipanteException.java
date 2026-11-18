package cl.duoc.syncpass.participantes;

import cl.duoc.syncpass.common.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Rechazo de aprovisionamiento con código estable (H3-DISENO 2.2).
 * El código viaja en {@code data.codigo} de la respuesta.
 */
@Getter
public class ParticipanteException extends BusinessException {

    private final String codigo;

    public ParticipanteException(HttpStatus status, String codigo, String message) {
        super(status, message);
        this.codigo = codigo;
    }

    public static ParticipanteException emailEnUso(String email) {
        return new ParticipanteException(HttpStatus.CONFLICT, "EMAIL_EN_USO",
                "El email " + email + " ya pertenece a otro usuario.");
    }

    public static ParticipanteException eventoNoExiste(Long eventoId) {
        return new ParticipanteException(HttpStatus.CONFLICT, "EVENTO_NO_EXISTE",
                "El evento " + eventoId + " no existe.");
    }
}
