package lk.ac.ruh.eng.campusevents.auth;

import lk.ac.ruh.eng.campusevents.auth.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private final AuthService auth;

    public UserController(AuthService auth) { this.auth = auth; }

    @GetMapping("/api/v1/users/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.currentUser(jwt.getSubject());
    }
}
