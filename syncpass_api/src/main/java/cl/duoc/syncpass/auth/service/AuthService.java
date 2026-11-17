package cl.duoc.syncpass.auth.service;

import cl.duoc.syncpass.auth.dto.LoginResponse;
import cl.duoc.syncpass.auth.dto.RegistroRequest;
import cl.duoc.syncpass.auth.dto.UsuarioResponse;
import cl.duoc.syncpass.auth.model.Usuario;
import cl.duoc.syncpass.auth.repository.UsuarioRepository;
import cl.duoc.syncpass.common.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    public static final Set<String> ROLES_VALIDOS = Set.of("Coordinador", "Monitor", "Asistente");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final ObjectProvider<CognitoIdentityProviderClient> cognitoProvider;

    private final String authMode;
    private final String jwtIssuer;
    private final long ttlMinutes;
    private final String clientId;
    private final String userPoolId;
    private final String clientSecret;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            ObjectProvider<CognitoIdentityProviderClient> cognitoProvider,
            @Value("${app.auth.mode:local}") String authMode,
            @Value("${app.jwt.issuer}") String jwtIssuer,
            @Value("${app.jwt.ttl-minutes:60}") long ttlMinutes,
            @Value("${aws.cognito.client-id:}") String clientId,
            @Value("${aws.cognito.user-pool-id:}") String userPoolId,
            @Value("${aws.cognito.client-secret:}") String clientSecret) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.cognitoProvider = cognitoProvider;
        this.authMode = authMode;
        this.jwtIssuer = jwtIssuer;
        this.ttlMinutes = ttlMinutes;
        this.clientId = clientId;
        this.userPoolId = userPoolId;
        this.clientSecret = clientSecret;
    }

    public boolean isCognitoMode() {
        return "cognito".equalsIgnoreCase(authMode);
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    public LoginResponse login(String email, String password) {
        return isCognitoMode() ? loginCognito(email, password) : loginLocal(email, password);
    }

    private LoginResponse loginLocal(String email, String password) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> BusinessException.unauthorized("Credenciales inválidas"));

        if (!usuario.isActivo()) {
            throw BusinessException.unauthorized("La cuenta está dada de baja");
        }
        if (usuario.getPasswordHash() == null
                || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw BusinessException.unauthorized("Credenciales inválidas");
        }
        return emitirTokens(usuario);
    }

    private LoginResponse loginCognito(String email, String password) {
        Map<String, String> params = new HashMap<>();
        params.put("USERNAME", email);
        params.put("PASSWORD", password);
        if (tieneClientSecret()) {
            params.put("SECRET_HASH", secretHash(email));
        }

        AuthenticationResultType result = cognito().initiateAuth(InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(clientId)
                .authParameters(params)
                .build()).authenticationResult();

        return LoginResponse.builder()
                .accessToken(result.accessToken())
                .idToken(result.idToken())
                .refreshToken(result.refreshToken())
                .expiresIn(result.expiresIn())
                .build();
    }

    // ------------------------------------------------------------------
    // Refresh
    // ------------------------------------------------------------------

    public LoginResponse refresh(String refreshToken) {
        if (isCognitoMode()) {
            Map<String, String> params = new HashMap<>();
            params.put("REFRESH_TOKEN", refreshToken);
            if (tieneClientSecret()) {
                throw BusinessException.badRequest("El refresh con client secret requiere SECRET_HASH del usuario");
            }
            AuthenticationResultType result = cognito().initiateAuth(InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .clientId(clientId)
                    .authParameters(params)
                    .build()).authenticationResult();

            return LoginResponse.builder()
                    .accessToken(result.accessToken())
                    .idToken(result.idToken())
                    .refreshToken(refreshToken)
                    .expiresIn(result.expiresIn())
                    .build();
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(refreshToken);
        } catch (Exception ex) {
            throw BusinessException.unauthorized("Refresh token inválido o expirado");
        }
        if (!"refresh".equals(jwt.getClaimAsString("typ"))) {
            throw BusinessException.unauthorized("El token entregado no es un refresh token");
        }

        Usuario usuario = usuarioRepository.findById(jwt.getSubject())
                .orElseThrow(() -> BusinessException.unauthorized("Usuario no encontrado"));
        if (!usuario.isActivo()) {
            throw BusinessException.unauthorized("La cuenta está dada de baja");
        }
        return emitirTokens(usuario);
    }

    // ------------------------------------------------------------------
    // Logout
    // ------------------------------------------------------------------

    public void logout(String accessToken) {
        if (!isCognitoMode()) {
            return;
        }
        cognito().globalSignOut(GlobalSignOutRequest.builder().accessToken(accessToken).build());
    }

    // ------------------------------------------------------------------
    // Registro y gestión de usuarios
    // ------------------------------------------------------------------

    @Transactional
    public UsuarioResponse registrar(RegistroRequest req) {
        validarRoles(req.getRoles());
        String email = req.getEmail().trim().toLowerCase();

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("Ya existe un usuario registrado con ese email");
        }

        Usuario usuario;
        if (isCognitoMode()) {
            String sub = registrarEnCognito(email, req.getPassword(), req.getNombre(), req.getRoles());
            usuario = Usuario.builder()
                    .id(sub)
                    .email(email)
                    .nombre(req.getNombre())
                    .roles(req.getRoles())
                    .activo(true)
                    .build();
        } else {
            usuario = Usuario.builder()
                    .id(UUID.randomUUID().toString())
                    .email(email)
                    .nombre(req.getNombre())
                    .passwordHash(passwordEncoder.encode(req.getPassword()))
                    .roles(req.getRoles())
                    .activo(true)
                    .build();
        }

        return toResponse(usuarioRepository.save(usuario));
    }

    private String registrarEnCognito(String email, String password, String nombre, Set<String> roles) {
        SignUpRequest.Builder signUp = SignUpRequest.builder()
                .clientId(clientId)
                .username(email)
                .password(password)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("name").value(nombre).build());

        if (tieneClientSecret()) {
            signUp.secretHash(secretHash(email));
        }

        String sub = cognito().signUp(signUp.build()).userSub();

        for (String rol : roles) {
            cognito().adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .groupName(rol)
                    .build());
        }

        cognito().adminConfirmSignUp(AdminConfirmSignUpRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .build());

        return sub;
    }

    public List<UsuarioResponse> listarUsuarios() {
        return usuarioRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public UsuarioResponse cambiarEstado(String id, boolean activo) {
        Usuario usuario = buscarPorId(id);

        if (isCognitoMode()) {
            if (activo) {
                cognito().adminEnableUser(AdminEnableUserRequest.builder()
                        .userPoolId(userPoolId).username(usuario.getEmail()).build());
            } else {
                cognito().adminDisableUser(AdminDisableUserRequest.builder()
                        .userPoolId(userPoolId).username(usuario.getEmail()).build());
            }
        }

        usuario.setActivo(activo);
        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public void eliminar(String id) {
        Usuario usuario = buscarPorId(id);

        if (isCognitoMode()) {
            cognito().adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId).username(usuario.getEmail()).build());
        }
        usuarioRepository.delete(usuario);
    }

    private Usuario buscarPorId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Usuario no encontrado"));
    }

    private void validarRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw BusinessException.badRequest("Debe indicar al menos un rol");
        }
        List<String> invalidos = roles.stream().filter(rol -> !ROLES_VALIDOS.contains(rol)).toList();
        if (!invalidos.isEmpty()) {
            throw BusinessException.badRequest("Roles inválidos: " + String.join(", ", invalidos)
                    + ". Válidos: " + String.join(", ", ROLES_VALIDOS));
        }
    }

    // ------------------------------------------------------------------
    // Tokens locales
    // ------------------------------------------------------------------

    private LoginResponse emitirTokens(Usuario usuario) {
        Instant now = Instant.now();
        Duration accessTtl = Duration.ofMinutes(ttlMinutes);

        JwtClaimsSet access = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .subject(usuario.getId())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim("email", usuario.getEmail())
                .claim("nombre", usuario.getNombre())
                .claim("roles", usuario.getRoles())
                .claim("typ", "access")
                .build();

        JwtClaimsSet idToken = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .subject(usuario.getId())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim("email", usuario.getEmail())
                .claim("name", usuario.getNombre())
                .claim("roles", usuario.getRoles())
                // La app Android lee los grupos del idToken desde este claim.
                .claim("groups", usuario.getRoles())
                .claim("typ", "id")
                .build();

        JwtClaimsSet refresh = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .subject(usuario.getId())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofDays(7)))
                .claim("typ", "refresh")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return LoginResponse.builder()
                .accessToken(jwtEncoder.encode(JwtEncoderParameters.from(header, access)).getTokenValue())
                .idToken(jwtEncoder.encode(JwtEncoderParameters.from(header, idToken)).getTokenValue())
                .refreshToken(jwtEncoder.encode(JwtEncoderParameters.from(header, refresh)).getTokenValue())
                .expiresIn((int) accessTtl.toSeconds())
                .build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public UsuarioResponse toResponse(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .roles(usuario.getRoles())
                .activo(usuario.isActivo())
                .build();
    }

    private CognitoIdentityProviderClient cognito() {
        CognitoIdentityProviderClient client = cognitoProvider.getIfAvailable();
        if (client == null) {
            throw BusinessException.badRequest("AUTH_MODE=cognito pero el cliente de Cognito no está configurado");
        }
        return client;
    }

    private boolean tieneClientSecret() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    private String secretHash(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((username + clientId).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar SECRET_HASH", ex);
        }
    }
}
