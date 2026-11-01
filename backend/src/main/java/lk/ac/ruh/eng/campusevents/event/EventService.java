package lk.ac.ruh.eng.campusevents.event;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.error.ApiException;
import lk.ac.ruh.eng.campusevents.event.dto.*;
import lk.ac.ruh.eng.campusevents.repository.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class EventService {
    private final EventRepository events;
    private final ClubRepository clubs;
    private final MongoTemplate mongo;
    private final EventIndexes indexes;
    public EventService(EventRepository events, ClubRepository clubs, MongoTemplate mongo, EventIndexes indexes) {
        this.events = events; this.clubs = clubs; this.mongo = mongo; this.indexes = indexes;
    }
    public List<EventResponse> publishedEvents() {
        var activeIds = clubs.findActive().stream().map(Club::getId).toList();
        return events.findByStatusAndClubIdInOrderByStartsAtAsc(EventStatus.PUBLISHED, activeIds)
                .stream().map(event -> EventResponse.from(event, false)).toList();
    }
    public EventResponse publishedEvent(String id) {
        Event event = find(id);
        if (event.getStatus() != EventStatus.PUBLISHED || !active(event.getClubId())) throw notFound();
        return EventResponse.from(event, false);
    }
    public List<EventResponse> clubEvents(String clubId) {
        Club club = club(clubId);
        if (club.getStatus() != ClubStatus.ACTIVE) throw notFound();
        return events.findByClubIdAndStatusOrderByStartsAtAsc(club.getId(), EventStatus.PUBLISHED)
                .stream().map(event -> EventResponse.from(event, false)).toList();
    }
    @PreAuthorize("@clubAuthorization.canView(authentication, #clubId)")
    public List<EventResponse> managedEvents(String clubId) {
        return events.findByClubIdOrderByStartsAtAsc(club(clubId).getId()).stream().map(event -> EventResponse.from(event, true)).toList();
    }
    @PreAuthorize("@eventAuthorization.canManage(authentication, #id)")
    public EventResponse managedEvent(String id) { return EventResponse.from(find(id), true); }

    @PreAuthorize("@clubAuthorization.canView(authentication, #clubId)")
    public EventResponse create(String clubId, @Valid EventRequest request) {
        Club club = club(clubId);
        requireActive(club.getId());
        Event event = new Event();
        event.setClubId(club.getId());
        event.setCreatedBy(new ObjectId(SecurityContextHolder.getContext().getAuthentication().getName()));
        event.setTitle(request.title()); event.setDescription(request.description()); event.setCategory(request.category());
        event.setVenue(request.venue()); event.setStartsAt(request.startAt()); event.setEndsAt(request.endAt());
        event.setCapacity(request.capacity()); event.setRegistrationDeadline(request.registrationDeadline());
        validate(event, true);
        indexes.ensure();
        return EventResponse.from(events.insert(event), true);
    }
    @PreAuthorize("@eventAuthorization.canManage(authentication, #id)")
    public EventResponse update(String id, @Valid EventPatchRequest request) {
        Event event = find(id);
        requireActive(event.getClubId());
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) throw conflict("Terminal events cannot be edited.");
        if (request.empty()) throw bad("event", "Provide at least one field to update.");
        if (request.title() != null) event.setTitle(request.title());
        if (request.description() != null) event.setDescription(request.description());
        if (request.category() != null) event.setCategory(request.category());
        if (request.venue() != null) event.setVenue(request.venue());
        if (request.startAt() != null) event.setStartsAt(request.startAt());
        if (request.endAt() != null) event.setEndsAt(request.endAt());
        if (request.capacity() != null) event.setCapacity(request.capacity());
        if (request.registrationDeadline() != null) event.setRegistrationDeadline(request.registrationDeadline());
        validate(event, false);
        return write(event, new Update().set("title", event.getTitle()).set("description", event.getDescription())
                .set("category", event.getCategory()).set("venue", event.getVenue()).set("startsAt", event.getStartsAt())
                .set("endsAt", event.getEndsAt()).set("capacity", event.getCapacity()).set("registrationDeadline", event.getRegistrationDeadline()));
    }
    @PreAuthorize("@eventAuthorization.canManage(authentication, #id)")
    public EventResponse status(String id, EventStatus status) {
        Event event = find(id);
        if (status == null) throw bad("status", "Status is required.");
        if (event.getStatus() == status) return EventResponse.from(event, true);
        boolean allowed = event.getStatus() == EventStatus.DRAFT && (status == EventStatus.PUBLISHED || status == EventStatus.CANCELLED)
                || event.getStatus() == EventStatus.PUBLISHED && (status == EventStatus.CANCELLED || status == EventStatus.COMPLETED);
        if (!allowed) throw conflict("That event status transition is not allowed.");
        if (status == EventStatus.PUBLISHED) { requireActive(event.getClubId()); validate(event, true); }
        if (status == EventStatus.COMPLETED && (event.getEndsAt() == null || event.getEndsAt().isAfter(Instant.now()))) {
            throw conflict("An event can be completed only after its end time.");
        }
        return write(event, new Update().set("status", status));
    }
    private EventResponse write(Event event, Update update) {
        Criteria version = event.getRevision() == 0 ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").exists(false))
                : Criteria.where("revision").is(event.getRevision());
        var result = mongo.findAndModify(Query.query(Criteria.where("_id").is(event.getId()).andOperator(version)),
                update.inc("revision", 1).set("updatedAt", Instant.now()), FindAndModifyOptions.options().returnNew(true), Event.class);
        if (result == null) throw conflict("The event changed during this request. Reload and try again.");
        return EventResponse.from(result, true);
    }
    private void validate(Event event, boolean opening) {
        if (event.getTitle() == null || event.getTitle().isBlank()) throw bad("title", "Title is required.");
        if (event.getDescription() == null || event.getDescription().isBlank()) throw bad("description", "Description is required.");
        if (event.getCategory() == null || event.getCategory().isBlank()) throw bad("category", "Category is required.");
        if (event.getVenue() == null || event.getVenue().isBlank()) throw bad("venue", "Venue is required.");
        Instant now = Instant.now();
        if (event.getStartsAt() == null || !event.getStartsAt().isAfter(now)) throw bad("startAt", "Start time must be in the future.");
        if (event.getEndsAt() == null || !event.getEndsAt().isAfter(event.getStartsAt())) throw bad("endAt", "End time must be after the start.");
        if (event.getCapacity() == null || event.getCapacity() < 1 || event.getCapacity() > 1000000) throw bad("capacity", "Capacity must be between 1 and 1000000.");
        if (event.getRegistrationDeadline() == null || event.getRegistrationDeadline().isAfter(event.getStartsAt())
                || opening && !event.getRegistrationDeadline().isAfter(now)) {
            throw bad("registrationDeadline", "Deadline must be no later than the start and must be in the future when creating or publishing.");
        }
    }
    private boolean active(ObjectId id) { return id != null && clubs.findById(id).map(club -> club.getStatus() == ClubStatus.ACTIVE).orElse(false); }
    private void requireActive(ObjectId id) { if (!active(id)) throw new ApiException(HttpStatus.CONFLICT, "INACTIVE_CLUB", "Events require an active club."); }
    private Event find(String id) {
        if (id == null || !ObjectId.isValid(id)) throw notFound();
        return events.findById(new ObjectId(id)).orElseThrow(EventService::notFound);
    }
    private Club club(String id) {
        if (id == null || !ObjectId.isValid(id)) throw notFound();
        return clubs.findById(new ObjectId(id)).orElseThrow(EventService::notFound);
    }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event or club not found."); }
    private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "EVENT_CONFLICT", message); }
    private static ApiException bad(String field, String message) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the highlighted fields.", Map.of(field, message)); }
}
