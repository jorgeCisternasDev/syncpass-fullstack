package cl.duoc.syncpass.health;

import cl.duoc.syncpass.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Value("${spring.application.name:syncpass-api}")
    private String serviceName;

    @Value("${app.auth.mode:local}")
    private String authMode;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", serviceName);
        data.put("status", "UP");
        data.put("authMode", authMode);
        data.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.ok(data, "Servicio operativo"));
    }
}
