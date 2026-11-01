package lk.ac.ruh.eng.campusevents.domain;

import java.time.Instant;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("events")
@CompoundIndex(name = "event_club_start", def = "{'clubId': 1, 'startsAt': 1}")
@CompoundIndex(name = "event_status_start", def = "{'status': 1, 'startsAt': 1}")
@CompoundIndex(name = "event_club_status_start", def = "{'clubId': 1, 'status': 1, 'startsAt': 1}")
public class Event extends AuditedDocument {
    private ObjectId clubId;
    private ObjectId createdBy;
    private String title;
    private String description;
    private String venue;
    private String category;
    private Instant registrationDeadline;
    private long revision;
    private Integer organizerCapacity = 0;
    private Instant organizingApplicationDeadline;
    private java.util.List<OrganizerApplication> organizerApplications = new java.util.ArrayList<>();
    public Integer getOrganizerCapacity() { return organizerCapacity == null ? 0 : organizerCapacity; }
    public void setOrganizerCapacity(Integer value) { organizerCapacity = value; }
    public Instant getOrganizingApplicationDeadline() { return organizingApplicationDeadline; }
    public void setOrganizingApplicationDeadline(Instant value) { organizingApplicationDeadline = value; }
    public java.util.List<OrganizerApplication> getOrganizerApplications() { return organizerApplications == null ? java.util.List.of() : organizerApplications; }
    public void setOrganizerApplications(java.util.List<OrganizerApplication> value) { organizerApplications = value; }
    public long getApprovedOrganizers() { return getOrganizerApplications().stream().filter(a -> a.getStatus() == ApplicationStatus.APPROVED).count(); }
    private java.util.List<Reservation> reservations = new java.util.ArrayList<>();

    public record Reservation(ObjectId token, ObjectId studentId, Instant createdAt, long sequence) { }
    public java.util.List<Reservation> getReservations() { return reservations == null ? java.util.List.of() : reservations; }
    public void setReservations(java.util.List<Reservation> reservations) { this.reservations = reservations; }
    public int getReservedSeats() { return getReservations().size(); }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Instant getRegistrationDeadline() { return registrationDeadline; }
    public void setRegistrationDeadline(Instant registrationDeadline) { this.registrationDeadline = registrationDeadline; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    @Indexed
    private Instant startsAt;
    private Instant endsAt;
    private Integer capacity;
    private EventStatus status = EventStatus.DRAFT;

    public ObjectId getClubId() { return clubId; }
    public void setClubId(ObjectId clubId) { this.clubId = clubId; }

    public ObjectId getCreatedBy() { return createdBy; }
    public void setCreatedBy(ObjectId createdBy) { this.createdBy = createdBy; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
}
