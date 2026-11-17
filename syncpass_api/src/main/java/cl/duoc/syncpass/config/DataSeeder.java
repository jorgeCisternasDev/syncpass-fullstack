package cl.duoc.syncpass.config;

import cl.duoc.syncpass.auth.model.Usuario;
import cl.duoc.syncpass.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Siembra tres usuarios ficticios cuando el modo es local y la tabla está vacía.
 * Permite demostrar el flujo completo sin depender de AWS.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.mode:local}")
    private String authMode;

    @Override
    public void run(String... args) {
        if (!"local".equalsIgnoreCase(authMode) || usuarioRepository.count() > 0) {
            return;
        }

        crear("coordinador@syncpass.cl", "Coord.2026", "Camila Rojas", Set.of("Coordinador"));
        crear("monitor@syncpass.cl", "Monitor.2026", "Diego Fuentes", Set.of("Monitor"));
        crear("asistente@syncpass.cl", "Asist.2026", "Valentina Soto", Set.of("Asistente"));
    }

    private void crear(String email, String password, String nombre, Set<String> roles) {
        usuarioRepository.save(Usuario.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .nombre(nombre)
                .passwordHash(passwordEncoder.encode(password))
                .roles(roles)
                .activo(true)
                .build());
    }
}
