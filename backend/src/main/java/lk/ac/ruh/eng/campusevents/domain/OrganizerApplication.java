package lk.ac.ruh.eng.campusevents.domain;

import java.time.Instant;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("organizer_applications")
@CompoundIndex(name = "application_user_club", def = "{'userId': 1, 'clubId': 1}")
@CompoundIndex(name = "application_club_status", def = "{'clubId': 1, 'status': 1}")
public class OrganizerApplication extends AuditedDocument {
    private ObjectId userId;
    private ObjectId clubId;
    private String motivation;
    private ApplicationStatus status = ApplicationStatus.PENDING;
    private ObjectId reviewedBy;
    private Instant reviewedAt;

    public ObjectId getUserId() { return userId; }
    public void setUserId(ObjectId userId) { this.userId = userId; }

    public ObjectId getClubId() { return clubId; }
    public void setClubId(ObjectId clubId) { this.clubId = clubId; }

    public String getMotivation() { return motivation; }
    public void setMotivation(String motivation) { this.motivation = motivation; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public ObjectId getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(ObjectId reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
