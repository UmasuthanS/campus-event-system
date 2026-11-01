package lk.ac.ruh.eng.campusevents;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import lk.ac.ruh.eng.campusevents.domain.Role;
import lk.ac.ruh.eng.campusevents.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest {
    private static final String DATABASE = "campus_auth_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "Test password 123";
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
        registry.add("spring.data.mongodb.auto-index-creation", () -> false);
        registry.add("app.seed.clubs.enabled", () -> false);
    }
    @Autowired TestRestTemplate client;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired MongoTemplate mongo;
    @Autowired JwtEncoder encoder;
    @Autowired org.springframework.core.env.Environment environment;
    @AfterAll
    void cleanup() {
        assertThat(mongo.getDb().getName()).isEqualTo(DATABASE);
        mongo.getDb().drop();
    }
    @Test
    void registrationLoginAndCurrentUserUseHashedPasswordsAndStudentRole() {
        var response = post("register", registration("101"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody().path("user").path("role").asText()).isEqualTo("STUDENT");
        assertThat(response.getBody().toString()).doesNotContain("password", "passwordHash", PASSWORD);
        var user = users.findByEmail("student101@example.test").orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();
        var profile = me(response.getBody().path("accessToken").asText());
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody().path("id").asText()).isEqualTo(user.getId().toHexString());
        assertThat(profile.getBody().toString()).doesNotContain("password");
        var login = post("login", Map.of("email", " STUDENT101@EXAMPLE.TEST ", "password", PASSWORD));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me(login.getBody().path("accessToken").asText()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me("invalid.token.signature").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(client.getForEntity("/api/v1/users/me", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test
    void duplicatesAndRoleInjectionAreRejected() {
        assertThat(post("register", registration("102")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var duplicate = new HashMap<String, Object>(registration("103"));
        duplicate.put("email", " STUDENT102@EXAMPLE.TEST ");
        assertThat(post("register", duplicate).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        duplicate = new HashMap<>(registration("104"));
        duplicate.put("registrationNumber", "eg/2026/102");
        assertThat(post("register", duplicate).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var elevated = new HashMap<String, Object>(registration("105"));
        elevated.put("role", "SUPER_ADMIN");
        assertThat(post("register", elevated).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(users.findByEmail("student105@example.test")).isEmpty();
    }
    @Test
    void validationAndInvalidCredentialsReturnConsistentErrors() {
        var bad = post("register", Map.of("email", "bad", "registrationNumber", "invalid", "fullName", " ", "password", "short"));
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody().path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(bad.getBody().path("errors").has("password")).isTrue();
        var unicode = new HashMap<String, Object>(registration("106"));
        unicode.put("password", "a1" + "界".repeat(24));
        assertThat(post("register", unicode).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("register", registration("107")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var wrong = post("login", Map.of("email", "student107@example.test", "password", "Incorrect password 42"));
        var unknown = post("login", Map.of("email", "missing@example.test", "password", PASSWORD));
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).isEqualTo(unknown.getBody());
        assertThat(wrong.getBody().path("status").asInt()).isEqualTo(401);
    }
    @Test
    void expiredTokensAndStaleRoleClaimsAreRejected() {
        var response = post("register", registration("108"));
        String accessToken = response.getBody().path("accessToken").asText();
        var user = users.findByEmail("student108@example.test").orElseThrow();
        var claims = JwtClaimsSet.builder().issuer(environment.getProperty("app.jwt.issuer"))
                .subject(user.getId().toHexString()).audience(List.of("campus-event-frontend"))
                .issuedAt(Instant.now().minusSeconds(100)).expiresAt(Instant.now().minusSeconds(10)).claim("role", "STUDENT").build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThat(me(expired).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        user.setRole(Role.CLUB_ADMIN);
        users.save(user);
        assertThat(post("login", Map.of("email", user.getEmail(), "password", PASSWORD)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me(accessToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    private Map<String, Object> registration(String suffix) {
        return Map.of("fullName", " Test Student ", "email", "student" + suffix + "@example.test", "registrationNumber", "EG/2026/" + suffix, "password", PASSWORD);
    }
    private ResponseEntity<JsonNode> post(String path, Map<String, ?> body) {
        return client.postForEntity("/api/v1/auth/" + path, body, JsonNode.class);
    }
    private ResponseEntity<JsonNode> me(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return client.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }
}
