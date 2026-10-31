package lk.ac.ruh.eng.campusevents.auth;

import lk.ac.ruh.eng.campusevents.domain.User;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class UserIndexes {
    private final MongoTemplate mongo;
    private boolean ready;

    public UserIndexes(MongoTemplate mongo) { this.mongo = mongo; }

    // Lazy creation keeps liveness startup independent of MongoDB.
    public synchronized void ensure() {
        if (!ready) {
            mongo.indexOps(User.class).ensureIndex(new Index().on("email", Direction.ASC).unique().named("email"));
            mongo.indexOps(User.class).ensureIndex(new Index().on("registrationNumber", Direction.ASC).unique().named("registrationNumber"));
            ready = true;
        }
    }
}
