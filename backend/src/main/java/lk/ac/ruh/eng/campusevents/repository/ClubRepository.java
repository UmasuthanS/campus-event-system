package lk.ac.ruh.eng.campusevents.repository;

import java.util.Optional;
import lk.ac.ruh.eng.campusevents.domain.Club;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClubRepository extends MongoRepository<Club, ObjectId> {
    Optional<Club> findByCode(String code);
}
