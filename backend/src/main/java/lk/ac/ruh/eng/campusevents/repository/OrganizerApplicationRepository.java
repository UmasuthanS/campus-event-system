package lk.ac.ruh.eng.campusevents.repository;

import java.util.List;
import lk.ac.ruh.eng.campusevents.domain.ApplicationStatus;
import lk.ac.ruh.eng.campusevents.domain.OrganizerApplication;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizerApplicationRepository extends MongoRepository<OrganizerApplication, ObjectId> {
    List<OrganizerApplication> findByEventIdOrderByCreatedAtAsc(ObjectId eventId);
    List<OrganizerApplication> findByUserIdOrderByCreatedAtDesc(ObjectId userId);
    List<OrganizerApplication> findByUserIdAndClubId(ObjectId userId, ObjectId clubId);
    List<OrganizerApplication> findByClubIdAndStatus(ObjectId clubId, ApplicationStatus status);
}
