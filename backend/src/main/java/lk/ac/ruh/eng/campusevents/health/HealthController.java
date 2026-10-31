package lk.ac.ruh.eng.campusevents.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        // Process liveness only: intentionally independent of MongoDB availability.
        return Map.of("status", "UP");
    }
}
