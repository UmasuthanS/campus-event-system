package lk.ac.ruh.eng.campusevents.organizer;

import java.time.Instant;
import java.util.*;
import lk.ac.ruh.eng.campusevents.authorization.EventAuthorization;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.error.ApiException;
import lk.ac.ruh.eng.campusevents.event.dto.EventResponse;
import lk.ac.ruh.eng.campusevents.repository.*;
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
import static lk.ac.ruh.eng.campusevents.organizer.OrganizerDtos.*;

@Service
public class OrganizerApplicationService {
    private final EventRepository events;
    private final ClubRepository clubs;
    private final UserRepository users;
    private final OrganizerApplicationRepository applications;
    private final EventAuthorization authorization;
    private final MongoTemplate mongo;
    private boolean indexed;
    public OrganizerApplicationService(EventRepository events, ClubRepository clubs, UserRepository users,
            OrganizerApplicationRepository applications, EventAuthorization authorization, MongoTemplate mongo) {
        this.events = events; this.clubs = clubs; this.users = users; this.applications = applications; this.authorization = authorization; this.mongo = mongo;
    }
    private synchronized void indexes() {
        if (indexed) return;
        mongo.indexOps(OrganizerApplication.class).ensureIndex(new Index().on("eventId", Direction.ASC).on("status", Direction.ASC).named("application_event_status"));
        mongo.indexOps(OrganizerApplication.class).ensureIndex(new Index().on("userId", Direction.ASC).on("createdAt", Direction.DESC).named("application_user_created"));
        mongo.indexOps(Event.class).ensureIndex(new Index().on("organizerApplications.userId", Direction.ASC).named("event_organizer_student"));
        indexed = true;
    }
    public List<TeamMember> team(String eventId) {
        Event event = event(eventId);
        if (event.getStatus() != EventStatus.PUBLISHED || clubs.findById(event.getClubId()).map(c -> c.getStatus() != ClubStatus.ACTIVE).orElse(true)) throw missing();
        return event.getOrganizerApplications().stream().filter(a -> a.getStatus() == ApplicationStatus.APPROVED)
                .map(a -> new TeamMember(users.findById(a.getStudentId()).map(User::getFullName).orElse("Former student"), a.getPreferredRole())).toList();
    }
    @PreAuthorize("hasRole('STUDENT')")
    public Response apply(String eventId, Apply request) {
        indexes();
        ObjectId student = identity();
        for (int attempt = 0; attempt < 32; attempt++) {
            Event event = event(eventId);
            requireOpen(event);
            if (event.getOrganizerApplications().stream().anyMatch(a -> student.equals(a.getStudentId()) && active(a))) throw conflict("You already have an active application for this event.");
            if (event.getReservations().stream().anyMatch(r -> student.equals(r.studentId()))) throw conflict("Cancel your RSVP before applying as an organizer.");
            OrganizerApplication application = new OrganizerApplication();
            application.setId(new ObjectId()); application.setUserId(student); application.setEventId(event.getId()); application.setClubId(event.getClubId());
            application.setPreferredRole(request.preferredRole().strip()); application.setMotivation(request.motivation().strip()); application.setExperience(request.experience().strip());
            application.setCreatedAt(Instant.now()); application.setUpdatedAt(application.getCreatedAt()); application.setSequence(event.getRevision() + 1);
            var ledger = new ArrayList<>(event.getOrganizerApplications()); ledger.add(application);
            if (write(event, ledger, true)) { project(application); return response(application, event, false); }
        }
        throw conflict("The event is busy. Please try again.");
    }
    @PreAuthorize("hasRole('STUDENT')")
    public List<Response> mine() {
        indexes();
        ObjectId student = identity();
        for (Event event : mongo.find(Query.query(Criteria.where("organizerApplications.userId").is(student)), Event.class)) {
            event.getOrganizerApplications().stream().filter(a -> student.equals(a.getStudentId())).forEach(this::project);
        }
        return applications.findByUserIdOrderByCreatedAtDesc(student).stream().filter(a -> a.getEventId() != null)
                .map(a -> response(a, events.findById(a.getEventId()).orElse(null), false)).toList();
    }
    @PreAuthorize("@eventAuthorization.canManage(authentication, #eventId)")
    public List<Response> list(String eventId) {
        indexes();
        Event event = event(eventId);
        event.getOrganizerApplications().forEach(this::project);
        return event.getOrganizerApplications().stream().map(a -> response(a, event, true)).toList();
    }
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'SUPER_ADMIN')")
    public Response review(String id, Review request) {
        if (request.status() != ApplicationStatus.APPROVED && request.status() != ApplicationStatus.REJECTED) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Choose APPROVED or REJECTED.");
        return change(id, request.status(), true);
    }
    @PreAuthorize("hasRole('STUDENT')")
    public Response withdraw(String id) { return change(id, ApplicationStatus.WITHDRAWN, false); }
    private Response change(String id, ApplicationStatus target, boolean reviewing) {
        if (!ObjectId.isValid(id)) throw missing();
        OrganizerApplication stored = applications.findById(new ObjectId(id)).orElseThrow(OrganizerApplicationService::missing);
        if (stored.getEventId() == null) throw missing();
        for (int attempt = 0; attempt < 32; attempt++) {
            Event event = event(stored.getEventId().toHexString());
            OrganizerApplication application = event.getOrganizerApplications().stream().filter(a -> a.getId().equals(stored.getId())).findFirst().orElseThrow(OrganizerApplicationService::missing);
            if (reviewing) {
                if (!authorization.canManage(SecurityContextHolder.getContext().getAuthentication(), event.getId().toHexString()) || identity().equals(application.getStudentId())) throw forbidden();
                if (application.getStatus() == target) return response(application, event, true);
                if (application.getStatus() != ApplicationStatus.PENDING) throw conflict("Only pending applications can be reviewed.");
                requireReviewable(event);
                if (target == ApplicationStatus.APPROVED && event.getApprovedOrganizers() >= event.getOrganizerCapacity()) throw conflict("The organizing team is full.");
                application.setReviewedAt(Instant.now()); application.setReviewedBy(identity());
            } else {
                if (!identity().equals(application.getStudentId())) throw forbidden();
                if (application.getStatus() == ApplicationStatus.WITHDRAWN) return response(application, event, false);
                if (!active(application) || !canWithdraw(event)) throw conflict("This application can no longer be withdrawn.");
            }
            application.setStatus(target); application.setUpdatedAt(Instant.now()); application.setSequence(event.getRevision() + 1);
            if (write(event, event.getOrganizerApplications(), false)) { project(application); return response(application, event, reviewing); }
        }
        throw conflict("The event changed. Please try again.");
    }
    // The shared event revision serializes organizer decisions, RSVP, and event configuration without transactions.
    private boolean write(Event event, List<OrganizerApplication> ledger, boolean applying) {
        Criteria revision = event.getRevision() == 0 ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").exists(false)) : Criteria.where("revision").is(event.getRevision());
        Criteria eligible = Criteria.where("_id").is(event.getId());
        if (event.getStatus() != EventStatus.CANCELLED) eligible = eligible.and("startsAt").gt(Instant.now());
        if (applying) eligible = eligible.and("organizingApplicationDeadline").gt(Instant.now());
        var updated = mongo.findAndModify(Query.query(eligible.andOperator(revision)),
                new Update().set("organizerApplications", ledger).inc("revision", 1).set("updatedAt", Instant.now()), FindAndModifyOptions.options().returnNew(true), Event.class);
        return updated != null;
    }
    private void project(OrganizerApplication a) {
        // The collection is a recoverable history projection; stale readers cannot undo a newer decision.
        Criteria older = new Criteria().orOperator(Criteria.where("sequence").lte(a.getSequence()), Criteria.where("sequence").exists(false));
        try {
            mongo.upsert(Query.query(Criteria.where("_id").is(a.getId()).andOperator(older)), new Update()
                    .set("userId", a.getUserId()).set("studentId", a.getStudentId()).set("eventId", a.getEventId()).set("clubId", a.getClubId())
                    .set("preferredRole", a.getPreferredRole()).set("motivation", a.getMotivation()).set("experience", a.getExperience())
                    .set("status", a.getStatus()).set("createdAt", a.getCreatedAt()).set("updatedAt", a.getUpdatedAt())
                    .set("reviewedBy", a.getReviewedBy()).set("reviewedAt", a.getReviewedAt()).set("sequence", a.getSequence()), OrganizerApplication.class);
        } catch (DuplicateKeyException ignored) { /* A newer ledger snapshot has already been projected. */ }
    }
    private Response response(OrganizerApplication a, Event event, boolean admin) {
        User user = admin ? users.findById(a.getStudentId()).orElse(null) : null;
        return new Response(a.getId().toHexString(), a.getStudentId().toHexString(), a.getEventId().toHexString(), a.getPreferredRole(), a.getMotivation(), a.getExperience(), a.getStatus(), a.getCreatedAt(), a.getReviewedAt(),
                a.getReviewedBy() == null ? null : a.getReviewedBy().toHexString(), active(a) && canWithdraw(event), user == null ? null : user.getFullName(), user == null ? null : user.getRegistrationNumber(), event == null ? null : EventResponse.from(event, false));
    }
    private boolean active(OrganizerApplication a) { return a.getStatus() == ApplicationStatus.PENDING || a.getStatus() == ApplicationStatus.APPROVED; }
    private boolean canWithdraw(Event e) { return e != null && (e.getStatus() == EventStatus.CANCELLED || e.getStatus() != EventStatus.COMPLETED && e.getStartsAt() != null && e.getStartsAt().isAfter(Instant.now())); }
    private void requireReviewable(Event e) {
        if (e.getStatus() != EventStatus.PUBLISHED || e.getStartsAt() == null || !e.getStartsAt().isAfter(Instant.now())
                || clubs.findById(e.getClubId()).map(c -> c.getStatus() != ClubStatus.ACTIVE).orElse(true)) throw conflict("An active club and published, future event are required.");
    }
    private void requireOpen(Event e) {
        requireReviewable(e);
        if (e.getOrganizerCapacity() <= 0 || e.getOrganizingApplicationDeadline() == null || !e.getOrganizingApplicationDeadline().isAfter(Instant.now())) throw conflict("Organizing applications are closed for this event.");
    }
    private Event event(String id) { if (!ObjectId.isValid(id)) throw missing(); return events.findById(new ObjectId(id)).orElseThrow(OrganizerApplicationService::missing); }
    private ObjectId identity() { return new ObjectId(SecurityContextHolder.getContext().getAuthentication().getName()); }
    private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "APPLICATION_CONFLICT", message); }
    private static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot manage this application."); }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Event or application not found."); }
}
