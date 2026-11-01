package lk.ac.ruh.eng.campusevents.registration;

import java.util.List;
import java.util.Map;
import lk.ac.ruh.eng.campusevents.error.ApiException;
import lk.ac.ruh.eng.campusevents.registration.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EventRegistrationController {
    private final EventRegistrationService registrations;
    public EventRegistrationController(EventRegistrationService registrations) { this.registrations = registrations; }
    @PostMapping("/events/{eventId}/registrations")
    public ResponseEntity<RegistrationResponse> register(@PathVariable String eventId, @RequestBody(required = false) Map<String, Object> body) {
        if (body != null && !body.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Student identity is derived from your login; do not supply registration fields.");
        return ResponseEntity.status(201).body(registrations.register(eventId));
    }
    @GetMapping("/users/me/registrations")
    public List<RegistrationResponse> mine() { return registrations.mine(); }
    @DeleteMapping("/registrations/{registrationId}")
    public RegistrationResponse cancel(@PathVariable String registrationId) { return registrations.cancel(registrationId); }
    @GetMapping("/events/{eventId}/registrations")
    public List<ParticipantResponse> participants(@PathVariable String eventId) { return registrations.participants(eventId); }
}
