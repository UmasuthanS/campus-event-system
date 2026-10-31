package lk.ac.ruh.eng.campusevents;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.*;
import lk.ac.ruh.eng.campusevents.auth.JwtService;
import lk.ac.ruh.eng.campusevents.auth.UserIndexes;
import lk.ac.ruh.eng.campusevents.config.SuperAdminBootstrap;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RbacIntegrationTest {
    private static final String DATABASE = "campus_rbac_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = UUID.randomUUID() + "a1";
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
        registry.add("app.seed.clubs.enabled", () -> false);
        registry.add("app.bootstrap.super-admin.enabled", () -> true);
        registry.add("app.bootstrap.super-admin.full-name", () -> "Bootstrap Admin");
        registry.add("app.bootstrap.super-admin.registration-number", () -> "EG/2026/9000");
        registry.add("app.bootstrap.super-admin.email", () -> "bootstrap@example.test");
        registry.add("app.bootstrap.super-admin.password", () -> PASSWORD);
    }
    @Autowired TestRestTemplate client;
    @Autowired UserRepository users;
    @Autowired ClubRepository clubs;
    @Autowired EventRepository events;
    @Autowired JwtService jwt;
    @Autowired MongoTemplate mongo;
    @Autowired SuperAdminBootstrap bootstrap;
    @Autowired PasswordEncoder passwords;
    @Autowired UserIndexes indexes;
    @Autowired Validator validator;
    private final Map<Role, User> accounts = new EnumMap<>(Role.class);
    private final Map<Role, String> tokens = new EnumMap<>(Role.class);
    private Club owned;
    private Club other;

    @BeforeAll
    void fixtures() {
        int number = 9100;
        for (Role role : Role.values()) {
            User user = new User();
            user.setFullName(role.name());
            user.setEmail(role.name().toLowerCase(Locale.ROOT) + "@example.test");
            user.setRegistrationNumber("EG/2026/" + number++);
            user.setPasswordHash(passwords.encode(PASSWORD));
            user.setRole(role);
            users.save(user);
            accounts.put(role, user);
            tokens.put(role, jwt.issue(user).accessToken());
        }
        owned = new Club();
        owned.setCode("OWNED");
        owned.setName("Owned club");
        owned.setAdminIds(Set.of(accounts.get(Role.CLUB_ADMIN).getId()));
        clubs.save(owned);
        other = new Club();
        other.setCode("OTHER");
        other.setName("Another club");
        clubs.save(other);
    }
    @AfterAll
    void cleanup() {
        assertThat(mongo.getDb().getName()).isEqualTo(DATABASE);
        mongo.getDb().drop();
    }
    @Test
    void roleMatrixAndUnauthenticatedAccessAreEnforced() {
        String[] paths = {"/student/access", "/club-admin/access", "/super-admin/access", "/users/me"};
        for (String path : paths) assertThat(get(path, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        for (Role role : Role.values()) {
            String token = tokens.get(role);
            assertThat(get("/student/access", token).getStatusCode()).isEqualTo(role == Role.STUDENT ? HttpStatus.OK : HttpStatus.FORBIDDEN);
            assertThat(get("/club-admin/access", token).getStatusCode()).isEqualTo(role == Role.STUDENT ? HttpStatus.FORBIDDEN : HttpStatus.OK);
            assertThat(get("/super-admin/access", token).getStatusCode()).isEqualTo(role == Role.SUPER_ADMIN ? HttpStatus.OK : HttpStatus.FORBIDDEN);
            assertThat(get("/users/me", token).getStatusCode()).isEqualTo(HttpStatus.OK);
            var login = client.postForEntity("/api/v1/auth/login", Map.of("email", accounts.get(role).getEmail(), "password", PASSWORD), JsonNode.class);
            assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(login.getBody().path("user").path("role").asText()).isEqualTo(role.name());
        }
        var denied = get("/super-admin/access", tokens.get(Role.STUDENT));
        assertThat(denied.getBody().path("code").asText()).isEqualTo("FORBIDDEN");
    }
    @Test
    void clubAdminsCanManageOnlyAssignedClubs() {
        String ownerPath = "/club-admin/clubs/" + owned.getId() + "/access";
        String otherPath = "/club-admin/clubs/" + other.getId() + "/access";
        assertThat(get(ownerPath, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(ownerPath, tokens.get(Role.STUDENT)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(ownerPath, tokens.get(Role.CLUB_ADMIN)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(otherPath, tokens.get(Role.CLUB_ADMIN)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(otherPath, tokens.get(Role.SUPER_ADMIN)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/club-admin/clubs/invalid/access", tokens.get(Role.CLUB_ADMIN)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/club-admin/clubs/" + new org.bson.types.ObjectId() + "/access", tokens.get(Role.SUPER_ADMIN)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test
    void publicDiscoveryExposesOnlyPublishedEventsAndPublicClubFields() {
        Event draft = new Event();
        draft.setTitle("Private draft");
        draft.setClubId(owned.getId());
        events.save(draft);
        Event published = new Event();
        published.setTitle("Public event");
        published.setClubId(owned.getId());
        published.setStartsAt(Instant.now());
        published.setStatus(EventStatus.PUBLISHED);
        events.save(published);
        var eventResponse = get("/events", null);
        assertThat(eventResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventResponse.getBody().toString()).contains("Public event").doesNotContain("Private draft", "createdBy");
        var clubResponse = get("/clubs", null);
        assertThat(clubResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clubResponse.getBody().toString()).contains("Owned club").doesNotContain("adminIds", "password");
        assertThat(get("/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test
    void bootstrapIsIdempotentAndNeverOverwritesCredentials() {
        User admin = users.findByEmail("bootstrap@example.test").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(passwords.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());
        User again = users.findByEmail(admin.getEmail()).orElseThrow();
        assertThat(again.getId()).isEqualTo(admin.getId());
        assertThat(again.getPasswordHash()).isEqualTo(admin.getPasswordHash());
    }
    @Test
    void bootstrapRefusesMissingCredentialsAndExistingStudentIdentities() {
        var missing = new SuperAdminBootstrap(users, indexes, passwords, validator, "", "", "", "");
        assertThatThrownBy(() -> missing.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
        User student = accounts.get(Role.STUDENT);
        var conflict = new SuperAdminBootstrap(users, indexes, passwords, validator, "Admin", student.getRegistrationNumber(), student.getEmail(), PASSWORD);
        assertThatThrownBy(() -> conflict.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(student.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
    }
    @Test
    void usersCannotAssignRolesAndOldTokensCannotAcquireNewPrivileges() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokens.get(Role.STUDENT));
        var result = client.exchange("/api/v1/users/me", HttpMethod.PUT,
                new HttpEntity<>(Map.of("role", "SUPER_ADMIN"), headers), JsonNode.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        User user = new User();
        user.setRegistrationNumber("EG/2026/9999");
        user.setEmail("changed@example.test");
        users.save(user);
        String studentToken = jwt.issue(user).accessToken();
        user.setRole(Role.SUPER_ADMIN);
        users.save(user);
        assertThat(get("/super-admin/access", studentToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String adminToken = jwt.issue(user).accessToken();
        user.setRole(Role.STUDENT);
        users.save(user);
        assertThat(get("/super-admin/access", adminToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    private ResponseEntity<JsonNode> get(String path, String token) {
        var headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return client.exchange("/api/v1" + path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }
}
