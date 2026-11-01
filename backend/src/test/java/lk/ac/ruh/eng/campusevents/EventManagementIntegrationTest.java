package lk.ac.ruh.eng.campusevents;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lk.ac.ruh.eng.campusevents.auth.JwtService;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.repository.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventManagementIntegrationTest {
    private static final String DATABASE = "campus_event_test_" + UUID.randomUUID().toString().replace("-", "");
    private final Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS).plusSeconds(172800);
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
        registry.add("spring.data.mongodb.auto-index-creation", () -> false);
        registry.add("app.seed.clubs.enabled", () -> false);
    }
    @Autowired TestRestTemplate client;
    @Autowired UserRepository users;
    @Autowired ClubRepository clubs;
    @Autowired EventRepository events;
    @Autowired MongoTemplate mongo;
    @Autowired JwtService jwt;
    private User admin;
    private String owner, otherAdmin, student, superAdmin;
    private Club own;
    @BeforeAll
    void setup() {
        admin = account("owner", Role.CLUB_ADMIN, 4000); owner = jwt.issue(admin).accessToken();
        otherAdmin = jwt.issue(account("other", Role.CLUB_ADMIN, 4001)).accessToken();
        student = jwt.issue(account("student", Role.STUDENT, 4002)).accessToken();
        superAdmin = jwt.issue(account("super", Role.SUPER_ADMIN, 4003)).accessToken();
        own = club("OWN", ClubStatus.ACTIVE);
    }
    @AfterAll
    void cleanup() { assertThat(mongo.getDb().getName()).isEqualTo(DATABASE); mongo.getDb().drop(); }
    @Test
    void draftsUseTrustedOwnershipAndAreHiddenFromPublicReads() {
        String id = create(own, owner);
        var detail = call(HttpMethod.GET, "/club-admin/events/" + id, owner, null);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().path("status").asText()).isEqualTo("DRAFT");
        assertThat(detail.getBody().path("createdBy").asText()).isEqualTo(admin.getId().toHexString());
        assertThat(detail.getBody().path("clubId").asText()).isEqualTo(own.getId().toHexString());
        assertThat(call(HttpMethod.GET, "/events/" + id, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/clubs/" + own.getId() + "/events", null, null).getBody().toString()).doesNotContain(id);
        assertThat(mongo.getCollection("events").find(new Document("_id", new ObjectId(id))).first().getDate("startsAt").toInstant()).isEqualTo(start);
        for (String forbidden : List.of("clubId", "createdBy", "status")) {
            var input = profile(); input.put(forbidden, "spoofed");
            assertThat(call(HttpMethod.POST, "/clubs/" + own.getId() + "/events", owner, input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
    @Test
    void publishingMakesEventsPublicWithoutExposingCreatorIdentity() {
        String id = create(own, owner);
        assertThat(status(id, "PUBLISHED", owner).getStatusCode()).isEqualTo(HttpStatus.OK);
        var publicEvent = call(HttpMethod.GET, "/events/" + id, null, null);
        assertThat(publicEvent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicEvent.getBody().toString()).doesNotContain("createdBy");
        assertThat(publicEvent.getBody().path("startAt").asText()).endsWith("Z");
        assertThat(call(HttpMethod.GET, "/events", null, null).getBody().toString()).contains(id);
        assertThat(call(HttpMethod.GET, "/clubs/" + own.getId() + "/events", null, null).getBody().toString()).contains(id);
    }
    @Test
    void invalidDatesCapacityAndMissingFieldsAreRejected() {
        var variants = List.<Map<String, Object>>of(Map.of("endAt", start.minusSeconds(1).toString()),
                Map.of("startAt", Instant.now().minusSeconds(1).toString()),
                Map.of("registrationDeadline", start.plusSeconds(1).toString()),
                Map.of("registrationDeadline", Instant.now().minusSeconds(1).toString()),
                Map.of("capacity", 0), Map.of("capacity", 2.5), Map.of("startAt", "invalid-date"), Map.of("title", " "));
        for (var invalid : variants) {
            var input = profile(); input.putAll(invalid);
            assertThat(call(HttpMethod.POST, "/clubs/" + own.getId() + "/events", owner, input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var missing = profile(); missing.remove("category");
        assertThat(call(HttpMethod.POST, "/clubs/" + own.getId() + "/events", owner, missing).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var invalid = profile(); invalid.put("endAt", start.minusSeconds(1).toString());
        assertThat(call(HttpMethod.POST, "/clubs/" + own.getId() + "/events", owner, invalid).getBody().path("errors").has("endAt")).isTrue();
    }
    @Test
    void partialEditsPreserveOwnershipAndValidateTheCombinedSchedule() {
        String id = create(own, owner);
        var result = call(HttpMethod.PATCH, "/events/" + id, owner, Map.of("title", "Updated title"));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().path("title").asText()).isEqualTo("Updated title");
        assertThat(result.getBody().path("createdBy").asText()).isEqualTo(admin.getId().toHexString());
        assertThat(call(HttpMethod.PATCH, "/events/" + id, owner, Map.of("endAt", start.minusSeconds(1).toString())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, "/events/" + id, owner, Map.of("clubId", new ObjectId().toHexString())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, "/events/" + id, owner, Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, "/events/" + id, superAdmin, Map.of("venue", "Main hall")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test
    void studentsAnonymousAndOtherClubAdminsCannotWriteEvents() {
        String id = create(own, owner);
        assertThat(call(HttpMethod.POST, "/clubs/" + own.getId() + "/events", null, profile()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.POST, "/clubs/" + own.getId() + "/events", student, profile()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/clubs/" + own.getId() + "/events", otherAdmin, profile()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        for (String token : List.of(student, otherAdmin)) {
            assertThat(call(HttpMethod.PATCH, "/events/" + id, token, Map.of("title", "Forbidden")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(status(id, "PUBLISHED", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(HttpMethod.GET, "/club-admin/events/" + id, token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
    @Test
    void inactiveClubsBlockCreationEditingAndPublicationButAllowCancellation() {
        Club target = club("INACTIVE", ClubStatus.ACTIVE);
        String id = create(target, owner);
        target.setStatus(ClubStatus.INACTIVE); clubs.save(target);
        for (String token : List.of(owner, superAdmin)) {
            assertThat(call(HttpMethod.POST, "/clubs/" + target.getId() + "/events", token, profile()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(call(HttpMethod.PATCH, "/events/" + id, token, Map.of("title", "No edit")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(status(id, "PUBLISHED", token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
        assertThat(status(id, "CANCELLED", owner).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/clubs/" + target.getId() + "/events", null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    @Test
    void terminalStatesAndCompletionTimingAreEnforced() {
        String cancelled = create(own, owner);
        assertThat(status(cancelled, "COMPLETED", owner).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(status(cancelled, "CANCELLED", owner).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(cancelled, "PUBLISHED", owner).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.PATCH, "/events/" + cancelled, owner, Map.of("title", "Cannot edit")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String completed = create(own, owner); status(completed, "PUBLISHED", owner);
        assertThat(status(completed, "COMPLETED", owner).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Event old = events.findById(new ObjectId(completed)).orElseThrow();
        old.setStartsAt(Instant.now().minusSeconds(7200)); old.setEndsAt(Instant.now().minusSeconds(3600)); events.save(old);
        assertThat(status(completed, "COMPLETED", owner).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/events/" + completed, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    @Test
    void indexesAndLegacyEventsWithoutRevisionRemainCompatible() {
        String id = create(own, owner);
        mongo.getCollection("events").updateOne(new Document("_id", new ObjectId(id)), new Document("$unset", new Document("revision", "")));
        assertThat(call(HttpMethod.PATCH, "/events/" + id, owner, Map.of("venue", "Legacy hall")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mongo.indexOps(Event.class).getIndexInfo()).extracting(info -> info.getName())
                .contains("event_status_start", "event_club_status_start", "event_club_start", "startsAt");
        assertThat(call(HttpMethod.GET, "/clubs/" + own.getId(), null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    private User account(String name, Role role, int number) {
        User user = new User(); user.setFullName(name); user.setRegistrationNumber("EG/2026/" + number);
        user.setEmail(name + "@example.test"); user.setRole(role); return users.save(user);
    }
    private Club club(String code, ClubStatus status) {
        Club club = new Club(); club.setCode(code); club.setName(code); club.setStatus(status); club.setAdminIds(Set.of(admin.getId())); return clubs.save(club);
    }
    private String create(Club club, String token) {
        var response = call(HttpMethod.POST, "/clubs/" + club.getId() + "/events", token, profile());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().path("id").asText();
    }
    private Map<String, Object> profile() {
        return new HashMap<>(Map.of("title", "Campus workshop", "description", "Event description", "category", "Workshop", "venue", "Lecture hall",
                "startAt", start.toString(), "endAt", start.plusSeconds(3600).toString(), "capacity", 50, "registrationDeadline", start.minusSeconds(3600).toString()));
    }
    private ResponseEntity<JsonNode> status(String id, String status, String token) { return call(HttpMethod.PATCH, "/events/" + id + "/status", token, Map.of("status", status)); }
    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders(); if (token != null) headers.setBearerAuth(token);
        return client.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
