package lk.ac.ruh.eng.campusevents.event.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;
import lk.ac.ruh.eng.campusevents.domain.Event;
import lk.ac.ruh.eng.campusevents.domain.EventStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventResponse(String id, String clubId, String title, String description, String category,
        String venue, Instant startAt, Instant endAt, Integer capacity, Instant registrationDeadline,
        EventStatus status, String createdBy, Instant createdAt, Instant updatedAt) {
    public static EventResponse from(Event event, boolean management) {
        return new EventResponse(event.getId().toHexString(), event.getClubId() == null ? null : event.getClubId().toHexString(),
                event.getTitle(), event.getDescription(), event.getCategory(), event.getVenue(), event.getStartsAt(), event.getEndsAt(),
                event.getCapacity(), event.getRegistrationDeadline(), event.getStatus(),
                management && event.getCreatedBy() != null ? event.getCreatedBy().toHexString() : null,
                event.getCreatedAt(), event.getUpdatedAt());
    }
}
