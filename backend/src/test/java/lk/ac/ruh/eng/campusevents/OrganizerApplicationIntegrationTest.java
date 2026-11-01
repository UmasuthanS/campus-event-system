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
class OrganizerApplicationIntegrationTest {
    private static final String DATABASE = "campus_organizer_test_" + UUID.randomUUID().toString().replace("-", "");
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
    @BeforeAll
    void setup() {
        User owner = account(9000, Role.CLUB_ADMIN); admin = jwt.issue(owner).accessToken();
        stranger = jwt.issue(account(9001, Role.CLUB_ADMIN)).accessToken();
        club = new Club(); club.setCode("IEEE"); club.setName("IEEE"); club.setAdminIds(Set.of(owner.getId())); clubs.save(club);
        for (int i = 0; i < 12; i++) { User user = account(9100 + i, Role.STUDENT); students.add(user); tokens.add(jwt.issue(user).accessToken()); }
    }
    @AfterAll
    void cleanup() { assertThat(mongo.getDb().getName()).isEqualTo(DATABASE); mongo.getDb().drop(); }
    private final Map<String, String> application = Map.of("preferredRole", "Logistics", "motivation", "I want to help my club", "experience", "Student society volunteer");
    private ResponseEntity<JsonNode> apply(Event event, int student) { return call(HttpMethod.POST, "/events/" + event.getId() + "/organizer-applications", tokens.get(student), application); }
    private String id(ResponseEntity<JsonNode> response) { assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED); return response.getBody().path("id").asText(); }
    private ResponseEntity<JsonNode> review(String id, String token, String status) { return call(HttpMethod.PATCH, "/organizer-applications/" + id + "/status", token, Map.of("status", status)); }
    @Autowired OrganizerApplicationRepository applications;
    @Test
    void studentAppliesAdminApprovesAndPublicTeamContainsOnlySafeFields() {
        Event event = event(2); String id = id(apply(event, 0));
        assertThat(applications.findById(new ObjectId(id)).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        var mine = call(HttpMethod.GET, "/users/me/organizer-applications", tokens.get(0), null);
        assertThat(mine.getBody().toString()).contains(id, "PENDING");
        assertThat(apply(event, 0).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(review(id, admin, "APPROVED").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applications.findById(new ObjectId(id)).orElseThrow().getReviewedBy()).isNotNull();
        assertThat(call(HttpMethod.GET, "/users/me/organizer-applications", tokens.get(0), null).getBody().toString()).contains("APPROVED");
        var team = call(HttpMethod.GET, "/events/" + event.getId() + "/organizing-team", null, null);
        assertThat(team.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(team.getBody().toString()).contains(students.get(0).getFullName(), "Logistics").doesNotContain("studentId", "motivation", "email", "registrationNumber");
    }
    @Test
    void permissionsProtectApplicationDataAndReviews() {
        Event event = event(2); String id = id(apply(event, 1));
        assertThat(review(id, stranger, "APPROVED").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(review(id, tokens.get(1), "APPROVED").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/events/" + event.getId() + "/organizer-applications", stranger, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/events/" + event.getId() + "/organizer-applications", tokens.get(0), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/events/" + event.getId() + "/organizer-applications", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/users/me/organizer-applications", tokens.get(0), null).getBody().toString()).doesNotContain(id);
        assertThat(call(HttpMethod.POST, "/events/" + event.getId() + "/organizer-applications", admin, application).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var list = call(HttpMethod.GET, "/events/" + event.getId() + "/organizer-applications", admin, null);
        assertThat(list.getBody().toString()).contains(students.get(1).getFullName()).doesNotContain("password", "email");
    }
    @Test
    void concurrentApprovalsCannotExceedTeamCapacity() throws Exception {
        Event event = event(2); var ids = new ArrayList<String>();
        for (int i = 0; i < 10; i++) ids.add(id(apply(event, i)));
        var results = parallel(10, i -> review(ids.get(i), admin, "APPROVED"));
        assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count()).isEqualTo(2);
        assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count()).isEqualTo(8);
        assertThat(events.findById(event.getId()).orElseThrow().getApprovedOrganizers()).isEqualTo(2);
        assertThat(applications.findByEventIdOrderByCreatedAtAsc(event.getId()).stream().filter(a -> a.getStatus() == ApplicationStatus.APPROVED).count()).isEqualTo(2);
        assertThat(call(HttpMethod.PATCH, "/events/" + event.getId(), admin, Map.of("organizerCapacity", 1)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test
    void concurrentDuplicateApplicationsCreateOneActiveEntry() throws Exception {
        Event event = event(3); var results = parallel(10, i -> apply(event, 0));
        assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count()).isEqualTo(1);
        assertThat(applications.findByEventIdOrderByCreatedAtAsc(event.getId())).hasSize(1);
    }
    @Test
    void rsvpAndActiveApplicationsAreMutuallyExclusiveEvenWhenRacing() throws Exception {
        Event event = event(2);
        var results = parallel(2, i -> i == 0 ? apply(event, 0) : register(event, tokens.get(0)));
        assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count()).isEqualTo(1);
        Event other = event(2); String id = id(apply(other, 1));
        assertThat(register(other, tokens.get(1)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        review(id, admin, "APPROVED");
        assertThat(register(other, tokens.get(1)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        call(HttpMethod.DELETE, "/organizer-applications/" + id, tokens.get(1), null);
        assertThat(register(other, tokens.get(1)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
    @Test
    void withdrawingApprovedApplicationReleasesExactlyOnePlace() throws Exception {
        Event event = event(1); String first = id(apply(event, 2)); String next = id(apply(event, 3));
        review(first, admin, "APPROVED");
        assertThat(call(HttpMethod.DELETE, "/organizer-applications/" + first, tokens.get(3), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var results = parallel(8, i -> call(HttpMethod.DELETE, "/organizer-applications/" + first, tokens.get(2), null));
        assertThat(results).allMatch(r -> r.getStatusCode() == HttpStatus.OK);
        assertThat(events.findById(event.getId()).orElseThrow().getApprovedOrganizers()).isZero();
        assertThat(applications.findById(new ObjectId(first)).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(review(first, admin, "APPROVED").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(review(next, admin, "APPROVED").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apply(event, 2).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
    @Test
    void rejectsClosedDraftInactiveEventsAndInvalidInput() {
        Event event = event(2); event.setOrganizingApplicationDeadline(Instant.now().minusSeconds(1)); events.save(event);
        assertThat(apply(event, 4).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        event.setOrganizingApplicationDeadline(Instant.now().plusSeconds(300)); event.setStatus(EventStatus.DRAFT); events.save(event);
        assertThat(apply(event, 4).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        event.setStatus(EventStatus.CANCELLED); events.save(event);
        assertThat(apply(event, 4).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Event valid = event(2);
        assertThat(call(HttpMethod.POST, "/events/" + valid.getId() + "/organizer-applications", tokens.get(4), Map.of("preferredRole", "", "motivation", "", "experience", "")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var spoof = new HashMap<String, String>(application); spoof.put("studentId", students.get(0).getId().toHexString());
        assertThat(call(HttpMethod.POST, "/events/" + valid.getId() + "/organizer-applications", tokens.get(4), spoof).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Club inactive = new Club(); inactive.setName("Inactive"); inactive.setCode("OFF"); inactive.setStatus(ClubStatus.INACTIVE); clubs.save(inactive); valid.setClubId(inactive.getId()); events.save(valid);
        assertThat(apply(valid, 4).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test
    void rejectionAndWithdrawalRulesAndRecovery() {
        Event event = event(2); String id = id(apply(event, 5));
        assertThat(review(id, admin, "WITHDRAWN").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(review(id, admin, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
        String replacement = id(apply(event, 5));
        applications.deleteById(new ObjectId(replacement));
        assertThat(call(HttpMethod.GET, "/users/me/organizer-applications", tokens.get(5), null).getBody().toString()).contains(replacement, "PENDING");
        event = events.findById(event.getId()).orElseThrow(); event.setStartsAt(Instant.now().minusSeconds(60)); events.save(event);
        assertThat(call(HttpMethod.DELETE, "/organizer-applications/" + replacement, tokens.get(5), null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        event.setStatus(EventStatus.CANCELLED); events.save(event);
        assertThat(call(HttpMethod.DELETE, "/organizer-applications/" + replacement, tokens.get(5), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test
    void promotedApplicantCannotReviewSelfButSuperAdminCanReview() {
        User applicant = account(8800, Role.STUDENT);
        Club own = new Club(); own.setName("Own club"); own.setCode("OWN"); clubs.save(own);
        Event event = event(2); event.setClubId(own.getId()); events.save(event);
        String applicationId = id(call(HttpMethod.POST, "/events/" + event.getId() + "/organizer-applications", jwt.issue(applicant).accessToken(), application));
        applicant.setRole(Role.CLUB_ADMIN); users.save(applicant); own.setAdminIds(Set.of(applicant.getId())); clubs.save(own);
        assertThat(review(applicationId, jwt.issue(applicant).accessToken(), "APPROVED").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(review(applicationId, jwt.issue(account(8801, Role.SUPER_ADMIN)).accessToken(), "APPROVED").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/events/" + event.getId(), null, null).getBody().toString()).doesNotContain("organizerApplications", "motivation", "studentId");
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
        event.setStartsAt(Instant.now().plusSeconds(86400)); event.setEndsAt(Instant.now().plusSeconds(90000)); event.setRegistrationDeadline(Instant.now().plusSeconds(3600)); event.setCapacity(20); event.setOrganizerCapacity(capacity); event.setOrganizingApplicationDeadline(Instant.now().plusSeconds(7200)); event.setStatus(EventStatus.PUBLISHED); return events.save(event);
    }
    private ResponseEntity<JsonNode> register(Event event, String token) { return call(HttpMethod.POST, "/events/" + event.getId() + "/registrations", token, null); }
    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) { HttpHeaders headers = new HttpHeaders(); if (token != null) headers.setBearerAuth(token); return client.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class); }
}
