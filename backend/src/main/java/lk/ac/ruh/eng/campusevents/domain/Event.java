package lk.ac.ruh.eng.campusevents.domain;

import java.time.Instant;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("events")
@CompoundIndex(name = "event_club_start", def = "{'clubId': 1, 'startsAt': 1}")
public class Event extends AuditedDocument {
    private ObjectId clubId;
    private ObjectId createdBy;
    private String title;
    private String description;
    private String venue;
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
