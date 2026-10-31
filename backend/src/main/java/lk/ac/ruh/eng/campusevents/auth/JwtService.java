package lk.ac.ruh.eng.campusevents.auth;

import java.time.Instant;
import java.util.List;
import lk.ac.ruh.eng.campusevents.auth.dto.AuthResponse;
import lk.ac.ruh.eng.campusevents.auth.dto.UserResponse;
import lk.ac.ruh.eng.campusevents.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttlSeconds;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.ttl-seconds}") long ttlSeconds) {
        if (ttlSeconds < 60 || ttlSeconds > 900) throw new IllegalArgumentException("JWT TTL must be between 60 and 900 seconds.");
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public AuthResponse issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toHexString())
                .audience(List.of("campus-event-frontend")).issuedAt(now).expiresAt(expiresAt)
                .claim("role", user.getRole().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expiresAt, UserResponse.from(user));
    }
}
