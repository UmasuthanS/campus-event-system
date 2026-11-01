package lk.ac.ruh.eng.campusevents;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import lk.ac.ruh.eng.campusevents.auth.JwtService;
import lk.ac.ruh.eng.campusevents.club.*;
import lk.ac.ruh.eng.campusevents.club.dto.ClubRequest;
import lk.ac.ruh.eng.campusevents.config.DevelopmentClubSeeder;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.repository.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubManagementIntegrationTest {
    private static final String DATABASE = "campus_club_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
        registry.add("app.seed.clubs.enabled", () -> true);
        registry.add("app.bootstrap.super-admin.enabled", () -> false);
    }
    @Autowired TestRestTemplate client;
    @Autowired UserRepository users;
    @Autowired ClubRepository clubs;
    @Autowired JwtService jwt;
    @Autowired MongoTemplate mongo;
    @Autowired DevelopmentClubSeeder seeder;
    @Autowired ClubService service;
    private final Map<Role, String> tokens = new EnumMap<>(Role.class);
    private User student;
    private int sequence;
    @BeforeAll
    void accounts() {
        int number = 8000;
        for (Role role : Role.values()) {
            User user = new User();
            user.setFullName(role.name()); user.setEmail(role.name().toLowerCase() + "@example.test");
            user.setRegistrationNumber("EG/2026/" + number++); user.setRole(role); users.save(user);
            if (role == Role.STUDENT) student = user;
            tokens.put(role, jwt.issue(user).accessToken());
        }
    }
    @AfterAll
    void cleanup() { assertThat(mongo.getDb().getName()).isEqualTo(DATABASE); mongo.getDb().drop(); }
    @Test
    void publicListingDetailsAndSeedsAreRealAndIdempotent() {
        var listing = call(HttpMethod.GET, "/clubs", null, null);
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listing.getBody().toString()).contains("IEEE", "ComES", "AIESEC").doesNotContain("adminIds", "nameKey");
        Club ieee = clubs.findByCode("IEEE").orElseThrow();
        ieee.setDescription("Preserved profile"); clubs.save(ieee);
        seeder.run(new DefaultApplicationArguments()); seeder.run(new DefaultApplicationArguments());
        Club reloaded = clubs.findByCode("IEEE").orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(ieee.getId());
        assertThat(reloaded.getDescription()).isEqualTo("Preserved profile");
        assertThat(call(HttpMethod.GET, "/clubs/" + ieee.getId(), null, null).getBody().path("status").asText()).isEqualTo("ACTIVE");
    }
    @Test
    void duplicateNamesShortNamesAndInvalidInputsAreRejected() {
        String id = create("Duplicate   Club", "DUP");
        assertThat(call(HttpMethod.POST, "/super-admin/clubs", tokens.get(Role.SUPER_ADMIN), profile(" duplicate club ", "OTHER")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.POST, "/super-admin/clubs", tokens.get(Role.SUPER_ADMIN), profile("Other club", "dup")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var bad = new HashMap<>(profile("Bad logo", "BAD")); bad.put("logoUrl", "javascript:alert(1)");
        assertThat(call(HttpMethod.POST, "/super-admin/clubs", tokens.get(Role.SUPER_ADMIN), bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        bad = new HashMap<>(profile("Injection", "INJ")); bad.put("adminIds", List.of(student.getId().toHexString()));
        assertThat(call(HttpMethod.POST, "/super-admin/clubs", tokens.get(Role.SUPER_ADMIN), bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        call(HttpMethod.DELETE, "/super-admin/clubs/" + id, tokens.get(Role.SUPER_ADMIN), null);
        assertThat(call(HttpMethod.POST, "/super-admin/clubs", tokens.get(Role.SUPER_ADMIN), profile("Duplicate Club", "DUP")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test
    void studentsAnonymousAndUnassignedAdminsCannotMutateClubs() {
        String id = freshClub();
        assertThat(call(HttpMethod.POST, "/super-admin/clubs", tokens.get(Role.STUDENT), profile("Student club", "STD")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PUT, "/club-admin/clubs/" + id, tokens.get(Role.STUDENT), profile("Edited club", "EDIT")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PUT, "/club-admin/clubs/" + id, null, profile("Edited club", "EDIT")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.PUT, "/club-admin/clubs/" + id, tokens.get(Role.CLUB_ADMIN), profile("Edited club", "EDIT")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PUT, "/super-admin/clubs/" + id + "/admins", tokens.get(Role.CLUB_ADMIN), Map.of("email", student.getEmail())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.DELETE, "/super-admin/clubs/" + id, tokens.get(Role.STUDENT), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test
    void assignedAdminsEditOnlyTheirClubsAndRevocationIsScoped() {
        User target = new User(); target.setEmail("assigned@example.test"); target.setRegistrationNumber("EG/2026/8999"); users.save(target);
        String first = freshClub(), second = freshClub(), other = freshClub();
        String path = "/super-admin/clubs/" + first + "/admins";
        assertThat(call(HttpMethod.PUT, path, tokens.get(Role.SUPER_ADMIN), Map.of("email", target.getEmail())).getStatusCode()).isEqualTo(HttpStatus.OK);
        call(HttpMethod.PUT, "/super-admin/clubs/" + second + "/admins", tokens.get(Role.SUPER_ADMIN), Map.of("email", target.getEmail()));
        target = users.findById(target.getId()).orElseThrow(); assertThat(target.getRole()).isEqualTo(Role.CLUB_ADMIN);
        String token = jwt.issue(target).accessToken();
        assertThat(call(HttpMethod.PUT, "/club-admin/clubs/" + first, token, profile("Assigned profile", "OWN")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.PUT, "/club-admin/clubs/" + other, token, profile("Wrong club", "WRONG")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        call(HttpMethod.DELETE, path + "/" + target.getId(), tokens.get(Role.SUPER_ADMIN), null);
        assertThat(call(HttpMethod.PUT, "/club-admin/clubs/" + first, token, profile("Revoked profile", "REV")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PUT, "/club-admin/clubs/" + second, token, profile("Other assignment", "KEPT")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/club-admin/clubs/" + first, token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test
    void deactivationIsSoftAndReactivationRestoresDiscovery() {
        String id = freshClub();
        assertThat(call(HttpMethod.DELETE, "/super-admin/clubs/" + id, tokens.get(Role.SUPER_ADMIN), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clubs.findById(new ObjectId(id)).orElseThrow().getStatus()).isEqualTo(ClubStatus.INACTIVE);
        assertThat(call(HttpMethod.GET, "/clubs/" + id, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/clubs", null, null).getBody().toString()).doesNotContain(id);
        assertThat(call(HttpMethod.PATCH, "/super-admin/clubs/" + id + "/status", tokens.get(Role.SUPER_ADMIN), Map.of("status", "ACTIVE")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/clubs/" + id, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test
    void legacyClubsKeepTheirIdentityAndTimestampsDuringBackfill() {
        ObjectId id = new ObjectId(); Date created = Date.from(Instant.parse("2026-01-01T00:00:00Z"));
        mongo.getCollection("clubs").insertOne(new Document("_id", id).append("name", "Legacy community").append("code", "LGC").append("description", "Original profile").append("createdAt", created));
        new ClubIndexes(mongo, clubs).ensure();
        Club club = clubs.findById(id).orElseThrow();
        assertThat(club.getShortName()).isEqualTo("LGC"); assertThat(club.getNameKey()).isEqualTo("legacy community");
        assertThat(club.getCreatedAt()).isEqualTo(created.toInstant()); assertThat(club.getDescription()).isEqualTo("Original profile");
        assertThat(call(HttpMethod.GET, "/clubs/" + id, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test
    void serviceLayerEnforcesRolesWithoutAController() {
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(student.getId().toHexString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
            assertThatThrownBy(() -> service.create(new ClubRequest("Blocked club", "BLOCK", "Description", "", ""))).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }
    private String freshClub() { int number = ++sequence; return create("Test club " + number, "TEST" + number); }
    private String create(String name, String shortName) {
        var response = call(HttpMethod.POST, "/super-admin/clubs", tokens.get(Role.SUPER_ADMIN), profile(name, shortName));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().path("id").asText();
    }
    private Map<String, Object> profile(String name, String shortName) { return Map.of("name", name, "shortName", shortName, "description", "Campus club description", "logoUrl", "", "contactEmail", "club@example.test"); }
    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders(); if (token != null) headers.setBearerAuth(token);
        return client.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
