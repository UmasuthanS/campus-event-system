package lk.ac.ruh.eng.campusevents.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lk.ac.ruh.eng.campusevents.domain.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtSecurityTest {
    private final JwtConfiguration config = new JwtConfiguration();
    private String secret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    @Test
    void signedTokensAreShortLivedAndContainNoPassword() {
        var key = config.jwtSigningKey(secret());
        User user = new User();
        user.setId(new ObjectId());
        user.setPasswordHash("must-never-be-in-token");
        var response = new JwtService(config.jwtEncoder(key), "issuer", 900).issue(user);
        var jwt = config.jwtDecoder(key, "issuer").decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toHexString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("STUDENT");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash", "email");
        assertThat(response.toString()).doesNotContain(response.accessToken());
    }
    @Test
    void invalidSignaturesExpiredTokensWrongIssuersAndAudiencesAreRejected() {
        var key = config.jwtSigningKey(secret());
        var decoder = config.jwtDecoder(key, "issuer");
        var encoder = config.jwtEncoder(key);
        assertThatThrownBy(() -> decoder.decode(token(encoder, "issuer", "campus-event-frontend", -10))).isInstanceOf(JwtException.class);
        String valid = token(encoder, "issuer", "campus-event-frontend", 100);
        assertThatThrownBy(() -> config.jwtDecoder(config.jwtSigningKey(secret()), "issuer").decode(valid)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(encoder, "wrong", "campus-event-frontend", 100))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(encoder, "issuer", "wrong", 100))).isInstanceOf(JwtException.class);
    }
    @Test
    void missingSecretsAndLongLifetimesAreRejected() {
        assertThatThrownBy(() -> config.jwtSigningKey("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.jwtSigningKey("not-base64!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(config.jwtEncoder(config.jwtSigningKey(secret())), "issuer", 3600)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void signedTokensMustIncludeExpiryAndValidSubject() {
        var key = config.jwtSigningKey(secret());
        var encoder = config.jwtEncoder(key);
        var decoder = config.jwtDecoder(key, "issuer");
        var noExpiry = JwtClaimsSet.builder().issuer("issuer").audience(List.of("campus-event-frontend"))
                .subject(new ObjectId().toHexString()).build();
        var noSubject = JwtClaimsSet.builder().issuer("issuer").audience(List.of("campus-event-frontend"))
                .expiresAt(Instant.now().plusSeconds(100)).build();
        for (var claims : List.of(noExpiry, noSubject)) {
            String encoded = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            assertThatThrownBy(() -> decoder.decode(encoded)).isInstanceOf(JwtException.class);
        }
    }
    private String token(JwtEncoder encoder, String issuer, String audience, long seconds) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(new ObjectId().toHexString())
                .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(seconds)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
