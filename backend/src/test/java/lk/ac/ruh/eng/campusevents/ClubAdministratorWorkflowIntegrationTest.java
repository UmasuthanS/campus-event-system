package lk.ac.ruh.eng.campusevents;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Validator;
import java.util.*;
import lk.ac.ruh.eng.campusevents.auth.*;
import lk.ac.ruh.eng.campusevents.config.DevelopmentClubAdminSeeder;
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
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubAdministratorWorkflowIntegrationTest {
    private static final String DATABASE = "campus_admin_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = UUID.randomUUID() + "a1";
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
        registry.add("app.seed.clubs.enabled", () -> true);
        registry.add("app.bootstrap.super-admin.enabled", () -> true);
        registry.add("app.bootstrap.super-admin.full-name", () -> "Super Admin Test");
        registry.add("app.bootstrap.super-admin.registration-number", () -> "EG/2026/5000");
        registry.add("app.bootstrap.super-admin.email", () -> "super@example.test");
        registry.add("app.bootstrap.super-admin.password", () -> PASSWORD);
        registry.add("app.seed.club-admins.enabled", () -> true);
        int number = 5001;
        for (String code : List.of("ieee", "comes", "aiesec")) {
            final int id = number++;
            String prefix = "app.seed.club-admins." + code + ".";
            registry.add(prefix + "name", () -> code + " Demo Admin");
            registry.add(prefix + "email", () -> code + "@example.test");
            registry.add(prefix + "registration-number", () -> "EG/2026/" + id);
            registry.add(prefix + "password", () -> PASSWORD);
        }
    }
    @Autowired UserRepository users;
    @Autowired ClubRepository clubs;
    @Autowired UserIndexes indexes;
    @Autowired JwtService jwt;
    @Autowired MongoTemplate mongo;
    @Autowired PasswordEncoder passwords;
    @Autowired Validator validator;
    @Autowired DevelopmentClubAdminSeeder seeder;
    @Autowired TestRestTemplate client;
    private String superToken;
    @BeforeAll
    void login() { superToken = jwt.issue(users.findByEmail("super@example.test").orElseThrow()).accessToken(); }
    @AfterAll
    void cleanup() { assertThat(mongo.getDb().getName()).isEqualTo(DATABASE); mongo.getDb().drop(); }
    @Test
    void environmentSeedCreatesHashedAdminsAndPreservesExistingAccounts() {
        for (String code : List.of("IEEE", "ComES", "AIESEC")) {
            User user = users.findByEmail(code.toLowerCase(Locale.ROOT) + "@example.test").orElseThrow();
            String hash = user.getPasswordHash();
            assertThat(user.getRole()).isEqualTo(Role.CLUB_ADMIN);
            assertThat(passwords.matches(PASSWORD, hash)).isTrue();
            assertThat(clubs.findByCode(code).orElseThrow().getAdminIds()).contains(user.getId());
            seeder.run(new DefaultApplicationArguments());
            assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        }
        seeder.run(new DefaultApplicationArguments());
        assertThat(users.findAll().stream().filter(user -> List.of("ieee@example.test", "comes@example.test", "aiesec@example.test").contains(user.getEmail())).count()).isEqualTo(3);
    }
    @Test
    void multipleAdminsAndScopedRevocationReuseTheExistingEndpoints() {
        var registration = client.postForEntity("/api/v1/auth/register", Map.of("fullName", "Second Admin", "email", "second@example.test", "registrationNumber", "EG/2026/6000", "password", PASSWORD), JsonNode.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String studentToken = registration.getBody().path("accessToken").asText();
        Club ieee = clubs.findByCode("IEEE").orElseThrow(); Club comes = clubs.findByCode("ComES").orElseThrow();
        String path = "/super-admin/clubs/" + ieee.getId() + "/admins";
        assertThat(call(HttpMethod.PUT, path, studentToken, Map.of("email", "second@example.test")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        for (int i = 0; i < 2; i++) assertThat(call(HttpMethod.PUT, path, superToken, Map.of("email", "second@example.test")).getStatusCode()).isEqualTo(HttpStatus.OK);
        User second = users.findByEmail("second@example.test").orElseThrow();
        assertThat(second.getRole()).isEqualTo(Role.CLUB_ADMIN);
        assertThat(clubs.findById(ieee.getId()).orElseThrow().getAdminIds()).containsExactlyInAnyOrder(second.getId(), users.findByEmail("ieee@example.test").orElseThrow().getId());
        call(HttpMethod.PUT, "/super-admin/clubs/" + comes.getId() + "/admins", superToken, Map.of("email", second.getEmail()));
        String token = jwt.issue(second).accessToken();
        assertThat(call(HttpMethod.GET, "/club-admin/clubs/" + ieee.getId(), token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.DELETE, path + "/" + second.getId(), superToken, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/club-admin/clubs/" + ieee.getId(), token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/club-admin/clubs/" + comes.getId(), token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test
    void clubAdminsCannotEditOtherClubsOrGrantAccess() {
        User ieeeAdmin = users.findByEmail("ieee@example.test").orElseThrow();
        String token = jwt.issue(ieeeAdmin).accessToken();
        Club ieee = clubs.findByCode("IEEE").orElseThrow(); Club comes = clubs.findByCode("ComES").orElseThrow();
        Map<String, Object> profile = Map.of("name", "Unauthorized edit", "shortName", "NOPE", "description", "Forbidden change", "logoUrl", "", "contactEmail", "");
        assertThat(call(HttpMethod.PUT, "/club-admin/clubs/" + comes.getId(), token, profile).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PUT, "/super-admin/clubs/" + comes.getId() + "/admins", token, Map.of("email", ieeeAdmin.getEmail())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.DELETE, "/super-admin/clubs/" + ieee.getId() + "/admins/" + ieeeAdmin.getId(), token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/club-admin/clubs/" + comes.getId(), null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(clubs.findById(comes.getId()).orElseThrow().getName()).isNotEqualTo("Unauthorized edit");
    }
    @Test
    void restartingTheDemoSeedDoesNotRestoreRevokedAccess() {
        var local = demo(environment("ieee", "Revoked Demo", "EG/2026/6100", "revoked@example.test"));
        local.run(new DefaultApplicationArguments());
        User user = users.findByEmail("revoked@example.test").orElseThrow();
        Club ieee = clubs.findByCode("IEEE").orElseThrow();
        call(HttpMethod.DELETE, "/super-admin/clubs/" + ieee.getId() + "/admins/" + user.getId(), superToken, null);
        local.run(new DefaultApplicationArguments());
        assertThat(clubs.findById(ieee.getId()).orElseThrow().getAdminIds()).doesNotContain(user.getId());
        assertThat(users.findById(user.getId()).orElseThrow().getRole()).isEqualTo(Role.CLUB_ADMIN);
    }
    @Test
    void invalidOrConflictingSeedCredentialsCannotPromoteExistingStudents() {
        User student = new User(); student.setFullName("Existing Student"); student.setEmail("existing@example.test"); student.setRegistrationNumber("EG/2026/6200"); users.insert(student);
        MockEnvironment env = environment("ieee", "New Demo", "EG/2026/6201", "new-demo@example.test");
        for (var entry : Map.of("name", "Existing Student", "registration-number", student.getRegistrationNumber(), "email", student.getEmail(), "password", PASSWORD).entrySet()) env.setProperty("app.seed.club-admins.comes." + entry.getKey(), entry.getValue());
        assertThatThrownBy(() -> demo(env).run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmail("new-demo@example.test")).isEmpty();
        assertThat(users.findById(student.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
        assertThatThrownBy(() -> demo(new MockEnvironment()).run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> demo(new MockEnvironment().withProperty("app.seed.club-admins.ieee.name", "Incomplete")).run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
    }
    private MockEnvironment environment(String code, String name, String number, String email) {
        return new MockEnvironment().withProperty("app.seed.club-admins." + code + ".name", name)
                .withProperty("app.seed.club-admins." + code + ".registration-number", number)
                .withProperty("app.seed.club-admins." + code + ".email", email).withProperty("app.seed.club-admins." + code + ".password", PASSWORD);
    }
    private DevelopmentClubAdminSeeder demo(MockEnvironment env) { return new DevelopmentClubAdminSeeder(env, validator, users, clubs, indexes, passwords, mongo); }
    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders(); if (token != null) headers.setBearerAuth(token);
        return client.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
