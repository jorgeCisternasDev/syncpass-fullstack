package cl.duoc.syncpass.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String authMode;
    private final String jwtSecret;
    private final String jwtIssuer;
    private final String allowedOrigins;
    private final String cognitoIssuer;

    public SecurityConfig(
            @Value("${app.auth.mode:local}") String authMode,
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.issuer}") String jwtIssuer,
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.auth.cognito.issuer:}") String cognitoIssuerExplicit,
            @Value("${aws.cognito.region:us-east-1}") String awsRegion,
            @Value("${aws.cognito.user-pool-id:}") String userPoolId) {
        this.authMode = authMode;
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;
        this.allowedOrigins = allowedOrigins;
        String derivedIssuer = userPoolId == null || userPoolId.isBlank()
                ? ""
                : "https://cognito-idp." + awsRegion + ".amazonaws.com/" + userPoolId;
        this.cognitoIssuer = cognitoIssuerExplicit != null && !cognitoIssuerExplicit.isBlank()
                ? cognitoIssuerExplicit
                : derivedIssuer;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**", "/api/health").permitAll()
                .requestMatchers("/api/sync/**").hasAnyRole("Coordinador", "Monitor", "Asistente")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public SecretKey jwtSigningKey() {
        return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        if ("cognito".equalsIgnoreCase(authMode)) {
            if (cognitoIssuer.isBlank()) {
                throw new IllegalStateException(
                        "AUTH_MODE=cognito requiere AWS_COGNITO_USER_POOL_ID y AWS_REGION");
            }
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(cognitoIssuer + "/.well-known/jwks.json").build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(cognitoIssuer));
            return decoder;
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtIssuer));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("cognito:groups");
            if (roles == null || roles.isEmpty()) {
                roles = jwt.getClaimAsStringList("roles");
            }
            if (roles == null || roles.isEmpty()) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> (org.springframework.security.core.GrantedAuthority)
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
