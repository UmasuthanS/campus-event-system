package lk.ac.ruh.eng.campusevents.registration;

import java.time.Instant;
import java.util.*;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.error.ApiException;
import lk.ac.ruh.eng.campusevents.event.dto.EventResponse;
import lk.ac.ruh.eng.campusevents.registration.dto.*;
import lk.ac.ruh.eng.campusevents.repository.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class EventRegistrationService {
    private final EventRepository events;
    private final ClubRepository clubs;
    private final EventRegistrationRepository registrations;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private boolean indexesReady;
    public EventRegistrationService(EventRepository events, ClubRepository clubs, EventRegistrationRepository registrations, UserRepository users, MongoTemplate mongo) {
        this.events = events; this.clubs = clubs; this.registrations = registrations; this.users = users; this.mongo = mongo;
    }
    private synchronized void indexes() {
        if (indexesReady) return;
        mongo.indexOps(EventRegistration.class).ensureIndex(new Index().on("eventId", Direction.ASC).on("userId", Direction.ASC).unique().named("registration_event_user"));
        mongo.indexOps(EventRegistration.class).ensureIndex(new Index().on("userId", Direction.ASC).on("createdAt", Direction.DESC).named("registration_user_created"));
        // Preserve pre-RSVP registration documents when upgrading an existing database.
        for (EventRegistration legacy : mongo.find(Query.query(Criteria.where("status").is(RegistrationStatus.CONFIRMED)
                .and("reservationToken").exists(false)), EventRegistration.class)) {
            for (int attempt = 0; attempt < 32; attempt++) {
                Event event = events.findById(legacy.getEventId()).orElse(null);
                if (event == null) break;
                var existing = event.getReservations().stream().filter(entry -> entry.studentId().equals(legacy.getStudentId())).findFirst();
                if (existing.isPresent()) { confirm(event, existing.get()); break; }
                var entry = new Event.Reservation(legacy.getId(), legacy.getStudentId(), legacy.getCreatedAt() == null ? Instant.now() : legacy.getCreatedAt(), event.getRevision() + 1);
                Event updated = mongo.findAndModify(Query.query(Criteria.where("_id").is(event.getId()).andOperator(revision(event))),
                        new Update().push("reservations", entry).inc("revision", 1), FindAndModifyOptions.options().returnNew(true), Event.class);
                if (updated != null) { confirm(updated, entry); break; }
                if (attempt == 31) throw conflict("EVENT_BUSY", "Legacy registrations are being upgraded. Please try again.");
            }
        }
        indexesReady = true;
    }
    @PreAuthorize("hasRole('STUDENT')")
    public RegistrationResponse register(String eventId) {
        indexes();
        ObjectId student = student();
        for (int attempt = 0; attempt < 32; attempt++) {
            Event event = event(eventId);
            if (event.getReservations().stream().anyMatch(entry -> entry.studentId().equals(student))) throw conflict("ALREADY_REGISTERED", "You are already registered for this event.");
            if (event.getOrganizerApplications().stream().anyMatch(a -> a.getStudentId().equals(student)
                    && (a.getStatus() == ApplicationStatus.PENDING || a.getStatus() == ApplicationStatus.APPROVED))) throw conflict("ORGANIZER_CONFLICT", "Withdraw your active organizer application before registering as a participant.");
            if (!open(event)) throw conflict("REGISTRATION_CLOSED", "Registration is closed for this event.");
            if (event.getCapacity() == null || event.getReservedSeats() >= event.getCapacity()) throw conflict("EVENT_FULL", "This event has no remaining seats.");
            Instant now = Instant.now();
            Event.Reservation reservation = new Event.Reservation(new ObjectId(), student, now, event.getRevision() + 1);
            Criteria eligible = Criteria.where("_id").is(event.getId()).and("status").is(EventStatus.PUBLISHED)
                    .and("registrationDeadline").gt(now).and("startsAt").gt(now).and("reservations.studentId").ne(student);
            Criteria capacity = Criteria.where("$expr").is(new Document("$lt", List.of(new Document("$size", new Document("$ifNull", List.of("$reservations", List.of()))), "$capacity")));
            Event reserved = mongo.findAndModify(Query.query(eligible.andOperator(revision(event), capacity)),
                    new Update().push("reservations", reservation).inc("revision", 1).set("updatedAt", now),
                    FindAndModifyOptions.options().returnNew(true), Event.class);
            if (reserved == null) continue;
            confirm(reserved, reservation);
            return response(registrations.findByEventIdAndUserId(reserved.getId(), student).orElseThrow(), reserved);
        }
        throw conflict("EVENT_BUSY", "The event changed during registration. Please try again.");
    }
    @PreAuthorize("hasRole('STUDENT')")
    public List<RegistrationResponse> mine() {
        indexes();
        ObjectId student = student();
        // The event ledger is authoritative; recover history projections after interrupted requests.
        for (Event event : mongo.find(Query.query(Criteria.where("reservations.studentId").is(student)), Event.class)) {
            event.getReservations().stream().filter(entry -> entry.studentId().equals(student)).forEach(entry -> confirm(event, entry));
        }
        return registrations.findByUserIdOrderByCreatedAtDesc(student).stream().map(registration -> {
            Event event = events.findById(registration.getEventId()).orElse(null);
            reconcile(registration, event);
            return response(registration, event);
        }).toList();
    }
    @PreAuthorize("hasRole('STUDENT')")
    public RegistrationResponse cancel(String id) {
        if (!ObjectId.isValid(id)) throw missing();
        EventRegistration registration = registrations.findById(new ObjectId(id)).orElseThrow(EventRegistrationService::missing);
        if (!student().equals(registration.getStudentId())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You can cancel only your own registration.");
        Event event = events.findById(registration.getEventId()).orElse(null);
        if (registration.getStatus() == RegistrationStatus.CANCELLED) return response(registration, event);
        Event.Reservation active = reservation(registration, event);
        if (active == null) { reconcile(registration, event); return response(registration, event); }
        if (!cancellable(event)) throw conflict("CANCELLATION_CLOSED", "Cancellation is allowed before the event starts, or when the event is cancelled.");
        // Pulling an immutable attempt token is idempotent and cannot release a later re-registration.
        Event updated = mongo.findAndModify(Query.query(Criteria.where("_id").is(event.getId()).and("reservations.token").is(active.token()).andOperator(
                        new Criteria().orOperator(Criteria.where("status").is(EventStatus.CANCELLED),
                                Criteria.where("status").ne(EventStatus.COMPLETED).and("startsAt").gt(Instant.now())))),
                new Update().pull("reservations", new Document("token", active.token())).inc("revision", 1).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Event.class);
        if (updated == null) {
            updated = events.findById(event.getId()).orElse(event);
            if (reservation(registration, updated) != null) throw conflict("CANCELLATION_CLOSED", "This event can no longer be cancelled.");
        }
        cancelled(registration);
        return response(registration, updated);
    }
    @PreAuthorize("@eventAuthorization.canManage(authentication, #eventId)")
    public List<ParticipantResponse> participants(String eventId) {
        indexes();
        Event event = event(eventId);
        event.getReservations().forEach(entry -> confirm(event, entry));
        var rows = registrations.findByEventIdOrderByCreatedAtAsc(event.getId());
        var people = new HashMap<ObjectId, User>();
        users.findAllById(rows.stream().map(EventRegistration::getStudentId).toList()).forEach(user -> people.put(user.getId(), user));
        return rows.stream().map(row -> {
            reconcile(row, event);
            User user = people.get(row.getStudentId());
            return new ParticipantResponse(row.getId().toHexString(), row.getStudentId().toHexString(), user == null ? "Deleted account" : user.getFullName(),
                    user == null ? "" : user.getRegistrationNumber(), row.getStatus(), row.getCreatedAt(), row.getCancelledAt());
        }).toList();
    }
    private void confirm(Event event, Event.Reservation entry) {
        Criteria older = new Criteria().orOperator(Criteria.where("reservationSequence").lt(entry.sequence()),
                Criteria.where("reservationSequence").exists(false), Criteria.where("reservationSequence").is(entry.sequence()).and("status").is(RegistrationStatus.CONFIRMED));
        Query query = Query.query(Criteria.where("eventId").is(event.getId()).and("userId").is(entry.studentId()).andOperator(older));
        try {
            mongo.upsert(query, new Update().set("eventId", event.getId()).set("userId", entry.studentId()).set("studentId", entry.studentId())
                    .set("reservationToken", entry.token()).set("reservationSequence", entry.sequence()).set("status", RegistrationStatus.CONFIRMED)
                    .set("createdAt", entry.createdAt()).set("updatedAt", Instant.now()).unset("cancelledAt"), EventRegistration.class);
        } catch (DuplicateKeyException exception) { /* A newer attempt or cancellation already owns this history row. */ }
    }
    private void reconcile(EventRegistration registration, Event event) {
        if (event != null && event.getRevision() < registration.getReservationSequence()) event = events.findById(event.getId()).orElse(null);
        if (registration.getStatus() == RegistrationStatus.CONFIRMED && registration.getReservationToken() != null && reservation(registration, event) == null) cancelled(registration);
    }
    private void cancelled(EventRegistration registration) {
        Instant now = registration.getCancelledAt() == null ? Instant.now() : registration.getCancelledAt();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(registration.getId()).and("reservationToken").is(registration.getReservationToken()).and("status").is(RegistrationStatus.CONFIRMED)),
                new Update().set("status", RegistrationStatus.CANCELLED).set("cancelledAt", now).set("updatedAt", now), EventRegistration.class);
        var persisted = registrations.findById(registration.getId()).orElse(registration);
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(Objects.equals(persisted.getReservationToken(), registration.getReservationToken()) && persisted.getCancelledAt() != null ? persisted.getCancelledAt() : now);
    }
    private Event.Reservation reservation(EventRegistration registration, Event event) {
        return event == null ? null : event.getReservations().stream().filter(entry -> entry.token().equals(registration.getReservationToken())).findFirst().orElse(null);
    }
    private RegistrationResponse response(EventRegistration registration, Event event) {
        return new RegistrationResponse(registration.getId().toHexString(), registration.getEventId().toHexString(), registration.getStudentId().toHexString(),
                registration.getStatus(), registration.getCreatedAt(), registration.getCancelledAt(), registration.getStatus() == RegistrationStatus.CONFIRMED && cancellable(event),
                event == null ? null : EventResponse.from(event, false));
    }
    private boolean open(Event event) {
        return event.getStatus() == EventStatus.PUBLISHED && event.getStartsAt() != null && event.getStartsAt().isAfter(Instant.now())
                && event.getRegistrationDeadline() != null && event.getRegistrationDeadline().isAfter(Instant.now())
                && clubs.findById(event.getClubId()).map(club -> club.getStatus() == ClubStatus.ACTIVE).orElse(false);
    }
    private boolean cancellable(Event event) {
        return event != null && (event.getStatus() == EventStatus.CANCELLED || event.getStatus() != EventStatus.COMPLETED
                && event.getStartsAt() != null && event.getStartsAt().isAfter(Instant.now()));
    }
    private Criteria revision(Event event) {
        return event.getRevision() == 0 ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").exists(false)) : Criteria.where("revision").is(event.getRevision());
    }
    private Event event(String id) {
        if (!ObjectId.isValid(id)) throw missing();
        return events.findById(new ObjectId(id)).orElseThrow(EventRegistrationService::missing);
    }
    private ObjectId student() { return new ObjectId(SecurityContextHolder.getContext().getAuthentication().getName()); }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "REGISTRATION_NOT_FOUND", "Event or registration not found."); }
    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
}
