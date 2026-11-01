package lk.ac.ruh.eng.campusevents.repository;

import java.util.List;
import java.util.Optional;
import lk.ac.ruh.eng.campusevents.domain.EventRegistration;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRegistrationRepository extends MongoRepository<EventRegistration, ObjectId> {
    List<EventRegistration> findByEventIdOrderByCreatedAtAsc(ObjectId eventId);
    Optional<EventRegistration> findByEventIdAndUserId(ObjectId eventId, ObjectId userId);
    List<EventRegistration> findByUserIdOrderByCreatedAtDesc(ObjectId userId);
}
