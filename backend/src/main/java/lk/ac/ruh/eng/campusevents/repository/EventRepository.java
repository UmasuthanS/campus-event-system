package lk.ac.ruh.eng.campusevents.repository;

import java.time.Instant;
import java.util.List;
import lk.ac.ruh.eng.campusevents.domain.Event;
import lk.ac.ruh.eng.campusevents.domain.EventStatus;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRepository extends MongoRepository<Event, ObjectId> {
    List<Event> findByStatusOrderByStartsAtAsc(EventStatus status);
    List<Event> findByClubIdOrderByStartsAtAsc(ObjectId clubId);
    List<Event> findByStartsAtBetweenOrderByStartsAtAsc(Instant from, Instant to);
}
