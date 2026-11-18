package cl.duoc.syncpass.participantes.service;

import cl.duoc.syncpass.auth.model.Usuario;
import cl.duoc.syncpass.auth.repository.UsuarioRepository;
import cl.duoc.syncpass.common.BusinessException;
import cl.duoc.syncpass.participantes.ParticipanteException;
import cl.duoc.syncpass.participantes.dto.ParticipanteRequest;
import cl.duoc.syncpass.participantes.dto.ParticipanteResponse;
import cl.duoc.syncpass.sync.repository.EventoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aprovisiona el participante local de la app como usuario del backend (H3-DISENO 2).
 * <p>
 * Decisiones de implementación:
 * <ul>
 *   <li><b>Identidad:</b> {@code usuarioUuid} se normaliza a UUID canónico en minúsculas, así el
 *       mismo participante reintentado con otra capitalización no duplica usuario.</li>
 *   <li><b>Idempotencia:</b> si el UUID ya existe, responde {@code creado=false} y no duplica ni
 *       cambia email, roles ni estado; solo rellena el nombre si estaba vacío (2.2).</li>
 *   <li><b>Email:</b> si falta se usa {@code participante+<uuid>@syncpass.local}; si el email
 *       pedido pertenece a otro UUID se rechaza con {@code 409 EMAIL_EN_USO}.</li>
 *   <li><b>Orden de validación:</b> formato (400) → evento (409) → email (409). El diseño no fija
 *       el orden cuando dos reglas fallan a la vez; se eligió el del recurso que se crea.</li>
 *   <li><b>Sin password:</b> el usuario nace con {@code passwordHash = null}; no puede iniciar
 *       sesión (H3-DISENO 6).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ParticipanteService {

    private static final String ROL_ASISTENTE = "Asistente";

    private static final String PREFIJO_SINTETICO = "participante+";
    private static final String DOMINIO_SINTETICO = "@syncpass.local";
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final UsuarioRepository usuarioRepository;
    private final EventoRepository eventoRepository;

    @Transactional
    public ParticipanteResponse registrar(ParticipanteRequest request) {
        String uuid = canonicalizar(request.usuarioUuid());

        if (!eventoRepository.existsById(request.eventoId())) {
            throw ParticipanteException.eventoNoExiste(request.eventoId());
        }

        String emailPedido = texto(request.email());
        Optional<Usuario> existente = usuarioRepository.findById(uuid);

        return existente
                .map(usuario -> actualizar(usuario, request.nombre(), emailPedido))
                .orElseGet(() -> crear(uuid, request.nombre(), emailPedido));
    }

    private ParticipanteResponse crear(String uuid, String nombre, String emailPedido) {
        String email = emailPedido != null ? emailPedido : emailSintetico(uuid);
        validarEmailLibre(email, uuid);

        usuarioRepository.save(Usuario.builder()
                .id(uuid)
                .email(email)
                .nombre(nombre.trim())
                .roles(Set.of(ROL_ASISTENTE))
                .activo(true)
                .build());

        return new ParticipanteResponse(uuid, true);
    }

    private ParticipanteResponse actualizar(Usuario usuario, String nombre, String emailPedido) {
        if (emailPedido != null) {
            validarEmailLibre(emailPedido, usuario.getId());
        }

        if (usuario.getNombre() == null || usuario.getNombre().isBlank()) {
            usuario.setNombre(nombre.trim());
            usuarioRepository.save(usuario);
        }

        return new ParticipanteResponse(usuario.getId(), false);
    }

    private void validarEmailLibre(String email, String uuid) {
        usuarioRepository.findByEmailIgnoreCase(email)
                .filter(otro -> !otro.getId().equals(uuid))
                .ifPresent(otro -> {
                    throw ParticipanteException.emailEnUso(email);
                });
    }

    private String canonicalizar(String valor) {
        String limpio = texto(valor);
        if (limpio == null || !UUID_CANONICO.matcher(limpio).matches()) {
            throw BusinessException.badRequest("usuarioUuid debe ser un UUID válido");
        }
        return UUID.fromString(limpio).toString().toLowerCase(Locale.ROOT);
    }

    private String emailSintetico(String uuid) {
        return PREFIJO_SINTETICO + uuid + DOMINIO_SINTETICO;
    }

    private String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
