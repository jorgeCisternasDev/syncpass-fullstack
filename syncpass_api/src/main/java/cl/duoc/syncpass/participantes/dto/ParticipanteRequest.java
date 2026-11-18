package cl.duoc.syncpass.participantes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Aprovisionamiento de un participante local como usuario del backend (H3-DISENO 2.2).
 * <p>
 * {@code usuarioUuid} lo genera la app y se convierte en {@code usuarios.id}; el documento
 * del participante no viaja al servidor (supuesto S-1).
 */
public record ParticipanteRequest(
        @NotBlank(message = "es obligatorio")
        String usuarioUuid,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        Long eventoId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 255, message = "no puede superar 255 caracteres")
        String nombre,

        /** Opcional: si falta se usa {@code participante+<uuid>@syncpass.local}. */
        @Size(max = 255, message = "no puede superar 255 caracteres")
        String email
) {
}
