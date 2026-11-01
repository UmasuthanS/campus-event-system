package lk.ac.ruh.eng.campusevents.discovery;

import java.time.Instant;
import java.util.List;
import lk.ac.ruh.eng.campusevents.domain.EventStatus;
import lk.ac.ruh.eng.campusevents.repository.EventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DiscoveryController {
    private final EventRepository events;

    public DiscoveryController(EventRepository events) {
        this.events = events;
    }

    @GetMapping("/events")
    public List<EventSummary> events() {
        return events.findByStatusOrderByStartsAtAsc(EventStatus.PUBLISHED).stream().map(event -> new EventSummary(
                event.getId().toHexString(), event.getClubId() == null ? null : event.getClubId().toHexString(),
                event.getTitle(), event.getDescription(), event.getVenue(), event.getStartsAt(), event.getEndsAt())).toList();
    }

    public record EventSummary(String id, String clubId, String title, String description, String venue, Instant startsAt, Instant endsAt) { }
}
