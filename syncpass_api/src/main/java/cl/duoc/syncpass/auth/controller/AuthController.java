package cl.duoc.syncpass.auth.controller;

import cl.duoc.syncpass.auth.dto.LoginRequest;
import cl.duoc.syncpass.auth.dto.LoginResponse;
import cl.duoc.syncpass.auth.dto.RefreshRequest;
import cl.duoc.syncpass.auth.dto.RegistroRequest;
import cl.duoc.syncpass.auth.dto.UsuarioResponse;
import cl.duoc.syncpass.auth.service.AuthService;
import cl.duoc.syncpass.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        // Contrato plano a propósito: la app Android (Api.kt) espera
        // {accessToken, refreshToken, idToken} sin envoltorio.
        return ResponseEntity.ok(authService.login(req.getEmail(), req.getPassword()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        LoginResponse tokens = authService.refresh(req.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(tokens, "Token renovado"));
    }

    @PostMapping("/registro")
    @PreAuthorize("hasRole('Coordinador')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> registrar(@Valid @RequestBody RegistroRequest req) {
        UsuarioResponse usuario = authService.registrar(req);
        return ResponseEntity.ok(ApiResponse.ok(usuario, "Usuario registrado exitosamente"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Token de autorización requerido"));
        }
        authService.logout(bearerToken.substring(7));
        return ResponseEntity.ok(ApiResponse.ok(null, "Sesión cerrada"));
    }
}
