package lk.ac.ruh.eng.campusevents.organizer;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static lk.ac.ruh.eng.campusevents.organizer.OrganizerDtos.*;

@RestController
@RequestMapping("/api/v1")
public class OrganizerApplicationController {
    private final OrganizerApplicationService service;
    public OrganizerApplicationController(OrganizerApplicationService service) { this.service = service; }
    @GetMapping("/events/{eventId}/organizing-team")
    public List<TeamMember> team(@PathVariable String eventId) { return service.team(eventId); }
    @PostMapping("/events/{eventId}/organizer-applications")
    public ResponseEntity<Response> apply(@PathVariable String eventId, @Valid @RequestBody Apply request) { return ResponseEntity.status(201).body(service.apply(eventId, request)); }
    @GetMapping("/users/me/organizer-applications")
    public List<Response> mine() { return service.mine(); }
    @GetMapping("/events/{eventId}/organizer-applications")
    public List<Response> list(@PathVariable String eventId) { return service.list(eventId); }
    @PatchMapping("/organizer-applications/{id}/status")
    public Response review(@PathVariable String id, @Valid @RequestBody Review request) { return service.review(id, request); }
    @DeleteMapping("/organizer-applications/{id}")
    public Response withdraw(@PathVariable String id) { return service.withdraw(id); }
}
