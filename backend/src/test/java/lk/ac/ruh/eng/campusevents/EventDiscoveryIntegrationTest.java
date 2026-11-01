package lk.ac.ruh.eng.campusevents;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.repository.*;
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
class EventDiscoveryIntegrationTest {
    private static final String DATABASE = "campus_discovery_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
        registry.add("app.seed.clubs.enabled", () -> false);
    }
    @Autowired TestRestTemplate client;
    @Autowired ClubRepository clubs;
    @Autowired EventRepository events;
    @Autowired MongoTemplate mongo;
    private Club ieee, comes;
    private Event future, other, past, ongoing, draft, crossing, endingBoundary, startingBoundary;
    private ZonedDateTime day;
    @BeforeAll
    void setup() {
        ieee = club("IEEE", ClubStatus.ACTIVE); comes = club("ComES", ClubStatus.ACTIVE);
        Club inactive = club("Hidden", ClubStatus.INACTIVE);
        day = LocalDate.now(ZoneId.of("Asia/Colombo")).plusDays(5).atStartOfDay(ZoneId.of("Asia/Colombo"));
        Instant start = day.plusHours(1).plusMinutes(30).toInstant();
        future = event(ieee, "Robotics [101]", "Workshop", start, start.plusSeconds(3600), EventStatus.PUBLISHED);
        other = event(comes, "Leadership session", "Leadership", start.plusSeconds(7200), start.plusSeconds(10800), EventStatus.PUBLISHED);
        past = event(ieee, "Past lecture", "Workshop", Instant.now().minusSeconds(10800), Instant.now().minusSeconds(7200), EventStatus.PUBLISHED);
        ongoing = event(ieee, "Ongoing discussion", "Workshop", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), EventStatus.PUBLISHED);
        draft = event(ieee, "Hidden draft", "Secret", start, start.plusSeconds(3600), EventStatus.DRAFT);
        event(ieee, "Hidden cancellation", "Secret", start, start.plusSeconds(3600), EventStatus.CANCELLED);
        event(inactive, "Inactive club event", "Secret", start, start.plusSeconds(3600), EventStatus.PUBLISHED);
        crossing = event(ieee, "Crossing midnight", "Workshop", day.minusHours(1).toInstant(), day.plusHours(1).toInstant(), EventStatus.PUBLISHED);
        endingBoundary = event(ieee, "Ends at range start", "Workshop", day.minusHours(2).toInstant(), day.toInstant(), EventStatus.PUBLISHED);
        startingBoundary = event(ieee, "Starts at range end", "Workshop", day.plusDays(1).toInstant(), day.plusDays(1).plusHours(1).toInstant(), EventStatus.PUBLISHED);
    }
    @AfterAll
    void cleanup() { assertThat(mongo.getDb().getName()).isEqualTo(DATABASE); mongo.getDb().drop(); }
    @Test
    void onlyPublishedEventsFromActiveClubsAreDiscoverable() {
        JsonNode result = list(Map.of());
        assertThat(result.toString()).contains("Robotics [101]", "Leadership session").doesNotContain("Hidden draft", "Hidden cancellation", "Inactive club event", "createdBy");
        assertThat(client.getForEntity("/api/v1/events/" + draft.getId(), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var categories = client.getForEntity("/api/v1/events/categories", JsonNode.class);
        assertThat(categories.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(categories.getBody().toString()).contains("Workshop", "Leadership").doesNotContain("Secret");
    }
    @Test
    void searchIsLiteralCaseInsensitiveAndCombinesWithClubAndCategoryFilters() {
        assertThat(ids(list(Map.of("search", "[101]")))).containsExactly(future.getId().toHexString());
        assertThat(ids(list(Map.of("search", " ROBOTICS ", "clubId", ieee.getId().toHexString(), "category", "workshop")))).containsExactly(future.getId().toHexString());
        assertThat(ids(list(Map.of("clubId", comes.getId().toHexString())))).containsExactly(other.getId().toHexString());
        assertThat(list(Map.of("search", ".*"))).isEmpty();
        assertThat(list(Map.of("category", "missing"))).isEmpty();
    }
    @Test
    void calendarUsesOverlappingExclusiveRangesAndColomboMidnightBoundaries() {
        var filter = Map.of("from", day.toOffsetDateTime().toString(), "to", day.plusDays(1).toOffsetDateTime().toString());
        var result = ids(list(filter));
        assertThat(result).contains(future.getId().toHexString(), crossing.getId().toHexString(), other.getId().toHexString())
                .doesNotContain(endingBoundary.getId().toHexString(), startingBoundary.getId().toHexString(), past.getId().toHexString());
        JsonNode detail = client.getForObject("/api/v1/events/" + future.getId(), JsonNode.class);
        Instant stored = Instant.parse(detail.path("startAt").asText());
        assertThat(stored.atZone(ZoneId.of("Asia/Colombo")).toLocalDate()).isEqualTo(day.toLocalDate());
        assertThat(stored.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(day.toLocalDate().minusDays(1));
        assertThat(stored.atZone(ZoneId.of("Asia/Colombo")).toLocalTime()).isEqualTo(LocalTime.of(1, 30));
    }
    @Test
    void upcomingIncludesOngoingAndPastAndSortingWork() {
        assertThat(ids(list(Map.of("period", "upcoming")))).contains(ongoing.getId().toHexString(), future.getId().toHexString()).doesNotContain(past.getId().toHexString());
        assertThat(ids(list(Map.of("period", "past")))).containsExactly(past.getId().toHexString());
        var asc = ids(list(Map.of("sort", "asc")));
        var desc = ids(list(Map.of("sort", "desc")));
        Collections.reverse(asc);
        assertThat(desc).isEqualTo(asc);
    }
    @Test
    void invalidFiltersAreRejectedAndClubRouteReusesTheSameFilters() {
        for (var filter : List.of(Map.of("clubId", "invalid"), Map.of("from", "invalid"), Map.of("period", "draft"), Map.of("sort", "unsafe"),
                Map.of("from", day.toInstant().toString(), "to", day.minusDays(1).toInstant().toString()))) {
            assertThat(get("/events", filter).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var scoped = get("/clubs/" + ieee.getId() + "/events", Map.of("search", "[101]", "category", "Workshop"));
        assertThat(scoped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(scoped.getBody())).containsExactly(future.getId().toHexString());
    }
    private JsonNode list(Map<String, String> filter) {
        var response = get("/events", filter); assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); return response.getBody();
    }
    private List<String> ids(JsonNode values) {
        var result = new ArrayList<String>(); values.forEach(value -> result.add(value.path("id").asText())); return result;
    }
    private ResponseEntity<JsonNode> get(String path, Map<String, String> filter) {
        String query = filter.entrySet().stream().map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return client.getForEntity(URI.create(client.getRootUri() + "/api/v1" + path + (query.isEmpty() ? "" : "?" + query)), JsonNode.class);
    }
    private Club club(String code, ClubStatus status) {
        Club club = new Club(); club.setCode(code); club.setName(code); club.setStatus(status); return clubs.save(club);
    }
    private Event event(Club club, String title, String category, Instant start, Instant end, EventStatus status) {
        Event event = new Event(); event.setClubId(club.getId()); event.setTitle(title); event.setCategory(category); event.setDescription("Discovery integration fixture");
        event.setVenue("Engineering auditorium"); event.setStartsAt(start); event.setEndsAt(end); event.setCapacity(50); event.setRegistrationDeadline(start.minusSeconds(3600)); event.setStatus(status); return events.save(event);
    }
}
