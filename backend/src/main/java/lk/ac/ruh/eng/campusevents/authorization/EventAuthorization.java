package lk.ac.ruh.eng.campusevents.authorization;

import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import lk.ac.ruh.eng.campusevents.repository.EventRepository;

@Component("eventAuthorization")
public class EventAuthorization {
    private final EventRepository events;
    private final ClubAuthorization clubs;
    public EventAuthorization(EventRepository events, ClubAuthorization clubs) { this.events = events; this.clubs = clubs; }
    public boolean canManage(Authentication authentication, String id) {
        if (id == null || !ObjectId.isValid(id)) return false;
        return events.findById(new ObjectId(id)).map(event -> event.getClubId() != null
                && clubs.canView(authentication, event.getClubId().toHexString())).orElse(false);
    }
}
