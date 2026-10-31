package lk.ac.ruh.eng.campusevents.config;

import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import lk.ac.ruh.eng.campusevents.domain.Club;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.clubs.enabled", havingValue = "true")
public class DevelopmentClubSeeder implements ApplicationRunner {
    private final MongoTemplate mongoTemplate;

    public DevelopmentClubSeeder(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        seedClub("IEEE", "IEEE");
        seedClub("ComES", "Computer Engineering Society");
        seedClub("AIESEC", "AIESEC");
    }

    private void seedClub(String code, String name) {
        Instant now = Instant.now();
        // Insert missing clubs only; never overwrite existing club details.
        mongoTemplate.upsert(Query.query(Criteria.where("code").is(code)),
                new Update().setOnInsert("code", code).setOnInsert("name", name)
                        .setOnInsert("createdAt", now).setOnInsert("updatedAt", now), Club.class);
    }
}
