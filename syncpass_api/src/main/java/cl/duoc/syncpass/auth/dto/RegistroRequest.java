package cl.duoc.syncpass.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class RegistroRequest {

    @NotBlank(message = "es obligatorio")
    @Email(message = "debe ser un email válido")
    private String email;

    @NotBlank(message = "es obligatorio")
    private String password;

    @NotBlank(message = "es obligatorio")
    private String nombre;

    @NotEmpty(message = "debe indicar al menos un rol")
    private Set<String> roles;
}
