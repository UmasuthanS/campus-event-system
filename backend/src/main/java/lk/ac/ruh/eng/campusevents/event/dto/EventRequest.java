package lk.ac.ruh.eng.campusevents.event.dto;

import java.time.Instant;
import jakarta.validation.constraints.*;

public record EventRequest(@NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 8000) String description, @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 160) String venue, @NotNull Instant startAt, @NotNull Instant endAt,
        @NotNull @Positive @Max(1000000) Integer capacity, @NotNull Instant registrationDeadline, @Min(0) @Max(1000) Integer organizerCapacity, Instant organizingApplicationDeadline) {
    public EventRequest(String title, String description, String category, String venue, Instant startAt, Instant endAt, Integer capacity, Instant registrationDeadline) {
        this(title, description, category, venue, startAt, endAt, capacity, registrationDeadline, null, null);
    }
    public EventRequest {
        title = trim(title); description = trim(description); category = trim(category); venue = trim(venue);
    }
    static String trim(String value) { return value == null ? null : value.strip(); }
}
