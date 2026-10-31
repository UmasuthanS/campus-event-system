package lk.ac.ruh.eng.campusevents;

import java.time.Instant;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import lk.ac.ruh.eng.campusevents.config.DevelopmentClubSeeder;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.repository.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Opt-in test: creates and removes only its own uniquely named database.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoPersistenceTest {
    private static final String DATABASE = "campus_stage3_test_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
        registry.add("app.seed.clubs.enabled", () -> true);
    }

    @Autowired MongoTemplate mongo;
    @Autowired UserRepository users;
    @Autowired ClubRepository clubs;
    @Autowired EventRepository events;
    @Autowired EventRegistrationRepository registrations;
    @Autowired OrganizerApplicationRepository applications;
    @Autowired DevelopmentClubSeeder seeder;
    @Autowired TestRestTemplate client;

    @AfterAll
    void cleanup() {
        assertThat(mongo.getDb().getName()).isEqualTo(DATABASE);
        mongo.getDb().drop();
    }

    @Test
    void databaseEndpointPingsMongo() {
        var response = client.getForEntity("/api/v1/health/database", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void clubSeedingIsIdempotentAndPreservesEdits() {
        assertThat(clubs.findAll()).extracting(Club::getCode).containsExactlyInAnyOrder("IEEE", "ComES", "AIESEC");
        Club club = clubs.findByCode("IEEE").orElseThrow();
        ObjectId id = club.getId();
        club.setDescription("Locally edited description");
        clubs.save(club);
        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());
        assertThat(clubs.count()).isEqualTo(3);
        Club reloaded = clubs.findByCode("IEEE").orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(id);
        assertThat(reloaded.getDescription()).isEqualTo("Locally edited description");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void uniqueUserIndexesAndUtcAuditTimestampsArePersisted() {
        User user = user("EG/2026/001", "student@example.test");
        users.save(user);
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        Instant created = user.getCreatedAt();
        user.setFullName("Updated student");
        users.save(user);
        User reloaded = users.findByRegistrationNumber("EG/2026/001").orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(user.getId());
        assertThat(reloaded.getCreatedAt()).isEqualTo(created);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(created);
        assertThat(users.findByEmail("student@example.test")).isPresent();
        assertThat(mongo.getCollection("users").find().first().getDate("createdAt").toInstant()).isEqualTo(created);
        assertThatThrownBy(() -> users.save(user("EG/2026/001", "another@example.test")))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> users.save(user("EG/2026/002", "student@example.test")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void eventReferencesDatesRegistrationUniquenessAndApplicationsRoundTrip() {
        ObjectId userId = new ObjectId();
        ObjectId clubId = clubs.findByCode("ComES").orElseThrow().getId();
        Event event = new Event();
        event.setClubId(clubId);
        event.setCreatedBy(userId);
        event.setTitle("Persistence test");
        event.setStartsAt(Instant.parse("2026-11-12T08:30:00Z"));
        event.setEndsAt(Instant.parse("2026-11-12T09:30:00Z"));
        events.save(event);
        assertThat(events.findByClubIdOrderByStartsAtAsc(clubId)).hasSize(1);
        assertThat(events.findByStartsAtBetweenOrderByStartsAtAsc(
                Instant.parse("2026-11-12T00:00:00Z"), Instant.parse("2026-11-13T00:00:00Z")))
                .extracting(Event::getId).containsExactly(event.getId());
        assertThat(mongo.getCollection("events").find().first().get("clubId")).isInstanceOf(ObjectId.class);
        EventRegistration registration = registration(event.getId(), userId);
        registrations.save(registration);
        assertThatThrownBy(() -> registrations.save(registration(event.getId(), userId)))
                .isInstanceOf(DuplicateKeyException.class);
        registration.setStatus(RegistrationStatus.CANCELLED);
        registrations.save(registration);
        assertThat(registrations.findByEventIdAndUserId(event.getId(), userId).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registrations.findByUserIdOrderByCreatedAtDesc(userId)).hasSize(1);
        OrganizerApplication application = new OrganizerApplication();
        application.setUserId(userId);
        application.setClubId(clubId);
        application.setMotivation("Test application");
        applications.save(application);
        assertThat(applications.findByUserIdAndClubId(userId, clubId)).hasSize(1);
        assertThat(applications.findByClubIdAndStatus(clubId, ApplicationStatus.PENDING)).hasSize(1);
        assertThat(mongo.indexOps(Event.class).getIndexInfo()).extracting(i -> i.getName())
                .contains("event_club_start", "startsAt");
        assertThat(mongo.indexOps(EventRegistration.class).getIndexInfo()).extracting(i -> i.getName())
                .contains("registration_event_user", "registration_user_created");
        assertThat(mongo.indexOps(OrganizerApplication.class).getIndexInfo()).extracting(i -> i.getName())
                .contains("application_user_club", "application_club_status");
    }

    private User user(String registrationNumber, String email) {
        User user = new User();
        user.setRegistrationNumber(registrationNumber);
        user.setEmail(email);
        user.setFullName("Test student");
        return user;
    }

    private EventRegistration registration(ObjectId eventId, ObjectId userId) {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(eventId);
        registration.setUserId(userId);
        return registration;
    }
}
