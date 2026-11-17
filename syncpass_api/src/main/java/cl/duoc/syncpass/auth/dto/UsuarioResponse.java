package cl.duoc.syncpass.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private String id;
    private String email;
    private String nombre;
    private Set<String> roles;
    private boolean activo;
}
