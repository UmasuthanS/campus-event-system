package lk.ac.ruh.eng.campusevents.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.mongodb.repository.Query;
import lk.ac.ruh.eng.campusevents.domain.Club;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClubRepository extends MongoRepository<Club, ObjectId> {
    @Query(value = "{'$or': [{'status': 'ACTIVE'}, {'status': {'$exists': false}}]}", sort = "{'name': 1}")
    List<Club> findActive();
    @Query("{'adminIds': ?0}")
    List<Club> findAssigned(ObjectId userId);
    Optional<Club> findByNameKey(String nameKey);
    Optional<Club> findByShortNameKey(String shortNameKey);
    Optional<Club> findByCode(String code);
}
