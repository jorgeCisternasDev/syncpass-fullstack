package cl.duoc.syncpass.auth.controller;

import cl.duoc.syncpass.auth.dto.UsuarioResponse;
import cl.duoc.syncpass.auth.service.AuthService;
import cl.duoc.syncpass.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<ApiResponse<List<UsuarioResponse>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(authService.listarUsuarios(), "Usuarios obtenidos"));
    }

    @PatchMapping("/{id}/baja")
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> darDeBaja(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(authService.cambiarEstado(id, false), "Usuario dado de baja"));
    }

    @PatchMapping("/{id}/alta")
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> darDeAlta(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(authService.cambiarEstado(id, true), "Usuario reactivado"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable String id) {
        authService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Usuario eliminado"));
    }
}
