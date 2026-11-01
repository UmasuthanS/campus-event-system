package lk.ac.ruh.eng.campusevents.authorization;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccessController {
    @GetMapping("/student/access")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, String> student() { return Map.of("access", "STUDENT"); }

    @GetMapping("/club-admin/access")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> clubAdmin() { return Map.of("access", "CLUB_ADMIN"); }

    @GetMapping("/super-admin/access")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, String> superAdmin() { return Map.of("access", "SUPER_ADMIN"); }

    @GetMapping("/club-admin/clubs/{clubId}/access")
    @PreAuthorize("@clubAuthorization.canManage(authentication, #clubId)")
    public Map<String, String> clubAccess(@PathVariable String clubId) {
        return Map.of("access", "CLUB_MANAGEMENT", "clubId", clubId);
    }
}
