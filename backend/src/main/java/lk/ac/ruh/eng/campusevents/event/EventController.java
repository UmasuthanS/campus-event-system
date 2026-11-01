package lk.ac.ruh.eng.campusevents.event;

import jakarta.validation.Valid;
import java.util.List;
import lk.ac.ruh.eng.campusevents.event.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EventController {
    private final EventService events;
    public EventController(EventService events) { this.events = events; }
    @GetMapping("/events")
    public List<EventResponse> list() { return events.publishedEvents(); }
    @GetMapping("/events/{id}")
    public EventResponse detail(@PathVariable String id) { return events.publishedEvent(id); }
    @GetMapping("/clubs/{clubId}/events")
    public List<EventResponse> byClub(@PathVariable String clubId) { return events.clubEvents(clubId); }
    @GetMapping("/club-admin/clubs/{clubId}/events")
    public List<EventResponse> managedList(@PathVariable String clubId) { return events.managedEvents(clubId); }
    @GetMapping("/club-admin/events/{id}")
    public EventResponse managedDetail(@PathVariable String id) { return events.managedEvent(id); }
    @PostMapping("/clubs/{clubId}/events")
    public ResponseEntity<EventResponse> create(@PathVariable String clubId, @Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(201).body(events.create(clubId, request));
    }
    @PatchMapping("/events/{id}")
    public EventResponse update(@PathVariable String id, @Valid @RequestBody EventPatchRequest request) { return events.update(id, request); }
    @PatchMapping("/events/{id}/status")
    public EventResponse status(@PathVariable String id, @Valid @RequestBody EventStatusRequest request) { return events.status(id, request.status()); }
}
