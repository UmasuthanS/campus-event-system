package lk.ac.ruh.eng.campusevents.repository;

import java.util.Optional;
import lk.ac.ruh.eng.campusevents.domain.User;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, ObjectId> {
    Optional<User> findByRegistrationNumber(String registrationNumber);
    Optional<User> findByEmail(String email);
}
