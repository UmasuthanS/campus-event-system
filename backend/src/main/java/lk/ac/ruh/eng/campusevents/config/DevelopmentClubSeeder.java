package lk.ac.ruh.eng.campusevents.config;

import java.time.Instant;
import lk.ac.ruh.eng.campusevents.club.ClubIndexes;
import lk.ac.ruh.eng.campusevents.domain.ClubStatus;
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

    private final ClubIndexes indexes;

    public DevelopmentClubSeeder(MongoTemplate mongoTemplate, ClubIndexes indexes) {
        this.mongoTemplate = mongoTemplate;
        this.indexes = indexes;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        indexes.ensure();
        seedClub("IEEE", "IEEE");
        seedClub("ComES", "Computer Engineering Society");
        seedClub("AIESEC", "AIESEC");
    }

    private void seedClub(String code, String name) {
        Instant now = Instant.now();
        // Insert missing clubs only; never overwrite existing club details.
        mongoTemplate.upsert(Query.query(new Criteria().orOperator(Criteria.where("code").is(code),
                        Criteria.where("shortNameKey").is(ClubIndexes.key(code)), Criteria.where("nameKey").is(ClubIndexes.key(name)))),
                new Update().setOnInsert("code", code).setOnInsert("name", name)
                        .setOnInsert("shortName", code).setOnInsert("nameKey", ClubIndexes.key(name))
                        .setOnInsert("shortNameKey", ClubIndexes.key(code)).setOnInsert("status", ClubStatus.ACTIVE)
                        .setOnInsert("description", name + " campus community.").setOnInsert("logoUrl", "").setOnInsert("contactEmail", "")
                        .setOnInsert("createdAt", now).setOnInsert("updatedAt", now), Club.class);
    }
}
