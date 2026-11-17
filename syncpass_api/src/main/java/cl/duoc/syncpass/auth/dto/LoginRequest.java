package cl.duoc.syncpass.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "es obligatorio")
    @Email(message = "debe ser un email válido")
    private String email;

    @NotBlank(message = "es obligatorio")
    private String password;
}
