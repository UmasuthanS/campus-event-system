package lk.ac.ruh.eng.campusevents.domain;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("event_registrations")
@CompoundIndex(name = "registration_event_user", def = "{'eventId': 1, 'userId': 1}", unique = true)
@CompoundIndex(name = "registration_user_created", def = "{'userId': 1, 'createdAt': -1}")
public class EventRegistration extends AuditedDocument {
    private ObjectId eventId;
    private ObjectId userId;
    private RegistrationStatus status = RegistrationStatus.CONFIRMED;

    public ObjectId getEventId() { return eventId; }
    public void setEventId(ObjectId eventId) { this.eventId = eventId; }

    public ObjectId getUserId() { return userId; }
    public void setUserId(ObjectId userId) { this.userId = userId; }

    public RegistrationStatus getStatus() { return status; }
    public void setStatus(RegistrationStatus status) { this.status = status; }
}
