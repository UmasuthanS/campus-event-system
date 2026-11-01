package lk.ac.ruh.eng.campusevents.event;

import lk.ac.ruh.eng.campusevents.domain.Event;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class EventIndexes {
    private final MongoTemplate mongo;
    private boolean ready;
    public EventIndexes(MongoTemplate mongo) { this.mongo = mongo; }
    public synchronized void ensure() {
        if (ready) return;
        mongo.indexOps(Event.class).ensureIndex(new Index().on("startsAt", Direction.ASC).named("startsAt"));
        mongo.indexOps(Event.class).ensureIndex(new Index().on("clubId", Direction.ASC).on("startsAt", Direction.ASC).named("event_club_start"));
        mongo.indexOps(Event.class).ensureIndex(new Index().on("status", Direction.ASC).on("startsAt", Direction.ASC).named("event_status_start"));
        mongo.indexOps(Event.class).ensureIndex(new Index().on("clubId", Direction.ASC).on("status", Direction.ASC)
                .on("startsAt", Direction.ASC).named("event_club_status_start"));
        ready = true;
    }
}
