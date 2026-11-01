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
class DashboardIntegrationTest {
    private static final String DATABASE = "campus_dashboard_test_" + UUID.randomUUID().toString().replace("-", "");
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
    @Autowired OrganizerApplicationRepository applications;
    @Autowired JwtService jwt;
    @Autowired MongoTemplate mongo;
    private Club assigned, other, inactive;
    private String ownerToken, otherToken, emptyToken, superToken, studentToken, secondToken;
    private User student, second;
    private Event future, past;
    @BeforeAll
    void accounts() {
        User owner = account(1000, Role.CLUB_ADMIN), stranger = account(1001, Role.CLUB_ADMIN);
        ownerToken = jwt.issue(owner).accessToken(); otherToken = jwt.issue(stranger).accessToken();
        emptyToken = jwt.issue(account(1002, Role.CLUB_ADMIN)).accessToken(); superToken = jwt.issue(account(1003, Role.SUPER_ADMIN)).accessToken();
        student = account(1004, Role.STUDENT); second = account(1005, Role.STUDENT);
        studentToken = jwt.issue(student).accessToken(); secondToken = jwt.issue(second).accessToken();
        assigned = club("IEEE", owner); other = club("ComES", stranger); inactive = club("AIESEC", owner);
        inactive.setStatus(ClubStatus.INACTIVE); clubs.save(inactive);
    }
    @BeforeEach
    void records() {
        registrations.deleteAll(); applications.deleteAll(); events.deleteAll();
        future = event(assigned, "Assigned upcoming", EventStatus.PUBLISHED, true);
        past = event(assigned, "Assigned past", EventStatus.PUBLISHED, false);
        event(assigned, "Assigned draft", EventStatus.DRAFT, true);
        Event hidden = event(other, "Other club private event", EventStatus.PUBLISHED, true);
        event(inactive, "Inactive club event", EventStatus.PUBLISHED, true);
        assertThat(call(HttpMethod.POST, "/events/" + future.getId() + "/registrations", studentToken, null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(call(HttpMethod.POST, "/events/" + hidden.getId() + "/registrations", secondToken, null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        past.setReservations(List.of(new Event.Reservation(new ObjectId(), student.getId(), Instant.now().minusSeconds(90000), 1)));
        past.setRevision(1); events.save(past);
        call(HttpMethod.GET, "/users/me/registrations", studentToken, null); // Recover the past reservation's history projection.
        String pending = apply(future, secondToken), approved = apply(hidden, studentToken);
        assertThat(call(HttpMethod.PATCH, "/organizer-applications/" + approved + "/status", otherToken, Map.of("status", "APPROVED")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applications.findById(new ObjectId(pending)).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }
    @AfterAll
    void cleanup() { assertThat(mongo.getDb().getName()).isEqualTo(DATABASE); mongo.getDb().drop(); }
    @Test
    void clubSummaryContainsOnlyAssignedClubsAndAccurateLedgerCounts() {
        var response = call(HttpMethod.GET, "/club-admin/dashboard", ownerToken, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = response.getBody();
        assertThat(data.path("totalClubs").asInt()).isEqualTo(2);
        assertThat(data.path("activeClubs").asInt()).isEqualTo(1);
        assertThat(data.path("events").size()).isEqualTo(4);
        assertThat(data.path("counts").path("totalEvents").asInt()).isEqualTo(4);
        assertThat(data.path("counts").path("upcomingEvents").asInt()).isEqualTo(2);
        assertThat(data.path("counts").path("participantRegistrations").asInt()).isEqualTo(2);
        assertThat(data.path("counts").path("pendingApplications").asInt()).isEqualTo(1);
        assertThat(data.toString()).doesNotContain("Other club private event", "motivation", "password", second.getEmail());
        var stranger = call(HttpMethod.GET, "/club-admin/dashboard", otherToken, null).getBody();
        assertThat(stranger.path("counts").path("totalEvents").asInt()).isEqualTo(1);
        assertThat(stranger.path("counts").path("approvedOrganizers").asInt()).isEqualTo(1);
        assertThat(stranger.toString()).doesNotContain("Assigned upcoming", "Assigned past");
    }
    @Test
    void superAdminGetsPlatformTotalsAndAssignments() {
        var response = call(HttpMethod.GET, "/super-admin/dashboard", superToken, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = response.getBody();
        assertThat(data.path("totalStudents").asLong()).isEqualTo(mongo.count(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("role").is(Role.STUDENT)), User.class));
        assertThat(data.path("totalClubs").asLong()).isEqualTo(clubs.count());
        assertThat(data.path("counts").path("totalEvents").asLong()).isEqualTo(events.count());
        assertThat(data.path("counts").path("publishedEvents").asInt()).isEqualTo(4);
        assertThat(data.path("counts").path("upcomingEvents").asInt()).isEqualTo(3);
        assertThat(data.path("counts").path("participantRegistrations").asInt()).isEqualTo(3);
        assertThat(data.path("counts").path("pendingApplications").asInt()).isEqualTo(1);
        assertThat(data.path("counts").path("approvedOrganizers").asInt()).isEqualTo(1);
        assertThat(data.path("events").isEmpty()).isTrue();
        assertThat(data.path("clubs").get(0).path("admins").size()).isGreaterThan(0);
        assertThat(data.toString()).doesNotContain("passwordHash", "motivation", "reservations");
    }
    @Test
    void studentDashboardApisArePrivateAndCancellationUpdatesAdminCounts() {
        var mine = call(HttpMethod.GET, "/users/me/registrations", studentToken, null).getBody();
        assertThat(mine.size()).isEqualTo(2);
        assertThat(mine.toString()).contains("Assigned upcoming", "Assigned past").doesNotContain("Other club private event");
        var ownApplications = call(HttpMethod.GET, "/users/me/organizer-applications", studentToken, null).getBody();
        assertThat(ownApplications.size()).isEqualTo(1);
        assertThat(ownApplications.get(0).path("status").asText()).isEqualTo("APPROVED");
        assertThat(ownApplications.get(0).path("studentId").asText()).isEqualTo(student.getId().toHexString());
        String id = registrations.findByEventIdAndUserId(future.getId(), student.getId()).orElseThrow().getId().toHexString();
        assertThat(call(HttpMethod.DELETE, "/registrations/" + id, studentToken, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/club-admin/dashboard", ownerToken, null).getBody().path("counts").path("participantRegistrations").asInt()).isEqualTo(1);
    }
    @Test
    void rolesAreEnforcedAndUnassignedAdminHasZeroCounts() {
        for (String path : List.of("/club-admin/dashboard", "/super-admin/dashboard", "/super-admin/users")) {
            assertThat(call(HttpMethod.GET, path, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(call(HttpMethod.GET, path, studentToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(call(HttpMethod.GET, "/super-admin/dashboard", ownerToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/super-admin/users", ownerToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var empty = call(HttpMethod.GET, "/club-admin/dashboard", emptyToken, null).getBody();
        assertThat(empty.path("totalClubs").asInt()).isZero();
        assertThat(empty.path("events").isEmpty()).isTrue();
        assertThat(empty.path("counts").path("totalEvents").asInt()).isZero();
    }
    @Test
    void userDirectorySearchPaginationAndValidationNeverExposeHashes() {
        var result = call(HttpMethod.GET, "/super-admin/users?search=1004", superToken, null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().path("items").size()).isEqualTo(1);
        assertThat(result.getBody().path("items").get(0).path("email").asText()).isEqualTo(student.getEmail());
        assertThat(result.getBody().toString()).doesNotContain("password");
        assertThat(call(HttpMethod.GET, "/super-admin/users?search=does-not-exist", superToken, null).getBody().path("items").isEmpty()).isTrue();
        assertThat(call(HttpMethod.GET, "/super-admin/users?page=-1", superToken, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.GET, "/super-admin/users?search=%5B", superToken, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        var page = call(HttpMethod.GET, "/super-admin/users?page=1", superToken, null).getBody();
        assertThat(page.path("items").isEmpty()).isTrue(); assertThat(page.path("hasNext").asBoolean()).isFalse();
        var extraIds = new ArrayList<ObjectId>();
        try {
            for (int i = 0; i < 21; i++) extraIds.add(account(2000 + i, Role.STUDENT).getId());
            var firstPage = call(HttpMethod.GET, "/super-admin/users", superToken, null).getBody();
            assertThat(firstPage.path("items").size()).isEqualTo(20);
            assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
            assertThat(call(HttpMethod.GET, "/super-admin/users?page=1", superToken, null).getBody().path("items").size()).isEqualTo(7);
        } finally { users.deleteAllById(extraIds); }
    }
    private String apply(Event event, String token) {
        var result = call(HttpMethod.POST, "/events/" + event.getId() + "/organizer-applications", token, Map.of("preferredRole", "Logistics", "motivation", "Help campus", "experience", ""));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED); return result.getBody().path("id").asText();
    }
    private Club club(String name, User owner) { Club club = new Club(); club.setName(name); club.setCode(name); club.setAdminIds(Set.of(owner.getId())); return clubs.save(club); }
    private User account(int number, Role role) { User user = new User(); user.setFullName("Dashboard " + number); user.setEmail(number + "@example.test"); user.setRegistrationNumber("EG/2026/" + number); user.setRole(role); return users.save(user); }
    private Event event(Club club, String title, EventStatus status, boolean future) {
        Event event = new Event(); event.setClubId(club.getId()); event.setTitle(title); event.setDescription("Dashboard integration test"); event.setCategory("Workshop"); event.setVenue("Main hall");
        event.setStartsAt(Instant.now().plusSeconds(future ? 86400 : -86400)); event.setEndsAt(event.getStartsAt().plusSeconds(3600)); event.setRegistrationDeadline(Instant.now().plusSeconds(3600)); event.setCapacity(10); event.setOrganizerCapacity(2); event.setOrganizingApplicationDeadline(Instant.now().plusSeconds(3600)); event.setStatus(status); return events.save(event);
    }
    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) { HttpHeaders headers = new HttpHeaders(); if (token != null) headers.setBearerAuth(token); return client.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class); }
}
