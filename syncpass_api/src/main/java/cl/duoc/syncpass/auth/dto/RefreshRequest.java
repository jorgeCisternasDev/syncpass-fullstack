package cl.duoc.syncpass.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {

    @NotBlank(message = "es obligatorio")
    private String refreshToken;
}
