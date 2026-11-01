package lk.ac.ruh.eng.campusevents.event.dto;

import java.time.Instant;
import jakarta.validation.constraints.*;

public record EventPatchRequest(@Size(min = 1, max = 160) String title,
        @Size(min = 1, max = 8000) String description, @Size(min = 1, max = 80) String category,
        @Size(min = 1, max = 160) String venue, Instant startAt, Instant endAt,
        @Positive @Max(1000000) Integer capacity, Instant registrationDeadline, @Min(0) @Max(1000) Integer organizerCapacity, Instant organizingApplicationDeadline) {
    public EventPatchRequest(String title, String description, String category, String venue, Instant startAt, Instant endAt, Integer capacity, Instant registrationDeadline) {
        this(title, description, category, venue, startAt, endAt, capacity, registrationDeadline, null, null);
    }
    public EventPatchRequest {
        title = EventRequest.trim(title); description = EventRequest.trim(description);
        category = EventRequest.trim(category); venue = EventRequest.trim(venue);
    }
    public boolean empty() { return title == null && description == null && category == null && venue == null
            && startAt == null && endAt == null && capacity == null && registrationDeadline == null && organizerCapacity == null && organizingApplicationDeadline == null; }
}
