package lk.ac.ruh.eng.campusevents;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
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
class EventRegistrationIntegrationTest {
    private static final String DATABASE = "campus_rsvp_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
        registry.add("app.seed.clubs.enabled", () -> false);
    }
    @Autowired TestRestTemplate client;
    @Autowired UserRepository users;
    @Autowired ClubRepository clubs;
    @Autowired EventRepository events;
    @Autowired EventRegistrationRepository registrations;
    @Autowired JwtService jwt;
    @Autowired MongoTemplate mongo;
    private final List<User> students = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private Club club;
    private String admin, stranger;
    private Event legacy;
    @BeforeAll
    void setup() {
        User owner = account(9000, Role.CLUB_ADMIN); admin = jwt.issue(owner).accessToken();
        stranger = jwt.issue(account(9001, Role.CLUB_ADMIN)).accessToken();
        club = new Club(); club.setCode("IEEE"); club.setName("IEEE"); club.setAdminIds(Set.of(owner.getId())); clubs.save(club);
        for (int i = 0; i < 12; i++) { User user = account(9100 + i, Role.STUDENT); students.add(user); tokens.add(jwt.issue(user).accessToken()); }
        legacy = event(5);
        EventRegistration old = new EventRegistration(); old.setEventId(legacy.getId()); old.setUserId(students.get(0).getId()); registrations.save(old);
    }
    @AfterAll
    void cleanup() { assertThat(mongo.getDb().getName()).isEqualTo(DATABASE); mongo.getDb().drop(); }
    @Test
    void rsvpIsPersistedAndDuplicateAndSpoofedIdentityAreRejected() {
        Event event = event(2);
        var response = register(event, tokens.get(0));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("studentId").asText()).isEqualTo(students.get(0).getId().toHexString());
        assertThat(response.getBody().path("event").path("remainingSeats").asInt()).isEqualTo(1);
        assertThat(registrations.findByEventIdAndUserId(event.getId(), students.get(0).getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(register(event, tokens.get(0)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.POST, "/events/" + event.getId() + "/registrations", tokens.get(1), Map.of("studentId", students.get(0).getId().toHexString())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.GET, "/users/me/registrations", tokens.get(1), null).getBody().toString()).doesNotContain(response.getBody().path("id").asText());
    }
    @Test
    void concurrentDifferentStudentsCannotOverbookCapacity() throws Exception {
        Event event = event(3);
        List<ResponseEntity<JsonNode>> results = parallel(12, index -> register(event, tokens.get(index)));
        assertThat(results.stream().filter(result -> result.getStatusCode() == HttpStatus.CREATED).count()).isEqualTo(3);
        assertThat(results.stream().filter(result -> result.getStatusCode() == HttpStatus.CONFLICT).count()).isEqualTo(9);
        assertThat(events.findById(event.getId()).orElseThrow().getReservedSeats()).isEqualTo(3);
        assertThat(registrations.findByEventIdOrderByCreatedAtAsc(event.getId())).hasSize(3);
        assertThat(call(HttpMethod.GET, "/events/" + event.getId(), null, null).getBody().path("remainingSeats").asInt()).isZero();
        assertThat(call(HttpMethod.PATCH, "/events/" + event.getId(), admin, Map.of("capacity", 2)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test
    void concurrentDuplicateSubmissionsCreateOneRegistration() throws Exception {
        Event event = event(10);
        var results = parallel(10, index -> register(event, tokens.get(0)));
        assertThat(results.stream().filter(result -> result.getStatusCode() == HttpStatus.CREATED).count()).isEqualTo(1);
        assertThat(events.findById(event.getId()).orElseThrow().getReservedSeats()).isEqualTo(1);
        assertThat(registrations.findByEventIdOrderByCreatedAtAsc(event.getId())).hasSize(1);
    }
    @Test
    void concurrentCancellationReleasesExactlyOneSeatAndReregistrationWorks() throws Exception {
        Event event = event(1);
        String id = register(event, tokens.get(0)).getBody().path("id").asText();
        assertThat(register(event, tokens.get(1)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var results = parallel(8, index -> call(HttpMethod.DELETE, "/registrations/" + id, tokens.get(0), null));
        assertThat(results).allMatch(result -> result.getStatusCode() == HttpStatus.OK);
        assertThat(events.findById(event.getId()).orElseThrow().getReservedSeats()).isZero();
        EventRegistration cancelled = registrations.findById(new ObjectId(id)).orElseThrow();
        assertThat(cancelled.getCancelledAt()).isNotNull(); assertThat(cancelled.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(register(event, tokens.get(0)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(events.findById(event.getId()).orElseThrow().getReservedSeats()).isEqualTo(1);
        assertThat(registrations.findById(new ObjectId(id)).orElseThrow().getCancelledAt()).isNull();
    }
    @Test
    void closedDraftAndCancelledEventsRejectNewRegistrations() {
        Event closed = event(5); closed.setRegistrationDeadline(Instant.now().minusSeconds(1)); events.save(closed);
        Event draft = event(5); draft.setStatus(EventStatus.DRAFT); events.save(draft);
        Event cancelled = event(5); cancelled.setStatus(EventStatus.CANCELLED); events.save(cancelled);
        for (Event event : List.of(closed, draft, cancelled)) {
            assertThat(register(event, tokens.get(0)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(events.findById(event.getId()).orElseThrow().getReservedSeats()).isZero();
        }
    }
    @Test
    void onlyOwnerCanCancelAndCancellationAfterStartIsBlocked() {
        Event event = event(5);
        String id = register(event, tokens.get(0)).getBody().path("id").asText();
        assertThat(call(HttpMethod.DELETE, "/registrations/" + id, tokens.get(1), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Event started = events.findById(event.getId()).orElseThrow(); started.setStartsAt(Instant.now().minusSeconds(1)); events.save(started);
        assertThat(call(HttpMethod.DELETE, "/registrations/" + id, tokens.get(0), null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.PATCH, "/events/" + event.getId() + "/status", admin, Map.of("status", "CANCELLED")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.DELETE, "/registrations/" + id, tokens.get(0), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test
    void participantsArePrivateAndOnlyAssignedAdminsCanReadThem() {
        Event event = event(5); register(event, tokens.get(0));
        String path = "/events/" + event.getId() + "/registrations";
        assertThat(call(HttpMethod.GET, path, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, path, tokens.get(0), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, path, stranger, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var response = call(HttpMethod.GET, path, admin, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toString()).contains(students.get(0).getFullName()).doesNotContain("password", "email");
        assertThat(call(HttpMethod.GET, "/events/" + event.getId(), null, null).getBody().toString()).doesNotContain("studentId", "reservations", "userId");
        assertThat(register(event, admin).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test
    void legacyRegistrationsAndInterruptedProjectionsAreRecovered() {
        call(HttpMethod.GET, "/users/me/registrations", tokens.get(0), null);
        assertThat(events.findById(legacy.getId()).orElseThrow().getReservedSeats()).isEqualTo(1);
        Event event = event(3);
        register(event, tokens.get(2));
        var saved = registrations.findByEventIdAndUserId(event.getId(), students.get(2).getId()).orElseThrow();
        registrations.deleteById(saved.getId());
        var mine = call(HttpMethod.GET, "/users/me/registrations", tokens.get(2), null);
        assertThat(mine.getBody().toString()).contains(event.getId().toHexString());
        var recovered = registrations.findByEventIdAndUserId(event.getId(), students.get(2).getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(events.findById(event.getId()).orElseThrow().getReservedSeats()).isEqualTo(1);
        mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("reservations", List.of())));
        call(HttpMethod.GET, "/users/me/registrations", tokens.get(2), null);
        assertThat(registrations.findById(recovered.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
    }
    private List<ResponseEntity<JsonNode>> parallel(int count, java.util.function.IntFunction<ResponseEntity<JsonNode>> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<ResponseEntity<JsonNode>>>();
            for (int i = 0; i < count; i++) { final int index = i; futures.add(executor.submit(() -> { ready.countDown(); start.await(); return action.apply(index); })); }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = new ArrayList<ResponseEntity<JsonNode>>(); for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS)); return results;
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    private User account(int number, Role role) { User user = new User(); user.setFullName("Student " + number); user.setEmail(number + "@example.test"); user.setRegistrationNumber("EG/2026/" + number); user.setRole(role); return users.save(user); }
    private Event event(int capacity) {
        Event event = new Event(); event.setClubId(club.getId()); event.setTitle("RSVP test event"); event.setDescription("Integration test"); event.setCategory("Workshop"); event.setVenue("Main hall");
        event.setStartsAt(Instant.now().plusSeconds(86400)); event.setEndsAt(Instant.now().plusSeconds(90000)); event.setRegistrationDeadline(Instant.now().plusSeconds(3600)); event.setCapacity(capacity); event.setStatus(EventStatus.PUBLISHED); return events.save(event);
    }
    private ResponseEntity<JsonNode> register(Event event, String token) { return call(HttpMethod.POST, "/events/" + event.getId() + "/registrations", token, null); }
    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) { HttpHeaders headers = new HttpHeaders(); if (token != null) headers.setBearerAuth(token); return client.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class); }
}
