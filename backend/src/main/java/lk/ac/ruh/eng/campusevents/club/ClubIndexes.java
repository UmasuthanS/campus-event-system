package lk.ac.ruh.eng.campusevents.club;

import java.util.Locale;
import lk.ac.ruh.eng.campusevents.domain.Club;
import lk.ac.ruh.eng.campusevents.repository.ClubRepository;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Component;

@Component
public class ClubIndexes {
    private final MongoTemplate mongo;
    private final ClubRepository clubs;
    private boolean ready;
    public ClubIndexes(MongoTemplate mongo, ClubRepository clubs) { this.mongo = mongo; this.clubs = clubs; }
    public static String key(String value) { return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }

    public synchronized void ensure() {
        if (ready) return;
        // Backfill legacy identifiers without replacing existing profile values or timestamps.
        for (Club club : clubs.findAll()) {
            missing(club, "shortName", club.getShortName());
            missing(club, "nameKey", key(club.getName()));
            missing(club, "shortNameKey", key(club.getShortName()));
            missing(club, "status", club.getStatus());
        }
        for (String field : new String[]{"nameKey", "shortNameKey"}) {
            mongo.indexOps(Club.class).ensureIndex(new Index().on(field, Direction.ASC).unique().sparse().named(field));
        }
        ready = true;
    }
    private void missing(Club club, String field, Object value) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(club.getId()).and(field).exists(false)),
                new Update().set(field, value), Club.class);
    }
}
