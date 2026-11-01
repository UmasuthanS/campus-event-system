package lk.ac.ruh.eng.campusevents.event.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;
import lk.ac.ruh.eng.campusevents.domain.Event;
import lk.ac.ruh.eng.campusevents.domain.EventStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventResponse(String id, String clubId, String title, String description, String category,
        String venue, Instant startAt, Instant endAt, Integer capacity, Instant registrationDeadline,
        EventStatus status, int remainingSeats, boolean registrationOpen, String createdBy, Instant createdAt, Instant updatedAt) {
    public static EventResponse from(Event event, boolean management) {
        return new EventResponse(event.getId().toHexString(), event.getClubId() == null ? null : event.getClubId().toHexString(),
                event.getTitle(), event.getDescription(), event.getCategory(), event.getVenue(), event.getStartsAt(), event.getEndsAt(),
                event.getCapacity(), event.getRegistrationDeadline(), event.getStatus(),
                Math.max(0, (event.getCapacity() == null ? 0 : event.getCapacity()) - event.getReservedSeats()),
                event.getStatus() == EventStatus.PUBLISHED && event.getRegistrationDeadline() != null
                        && event.getRegistrationDeadline().isAfter(Instant.now()) && event.getStartsAt() != null && event.getStartsAt().isAfter(Instant.now()),
                management && event.getCreatedBy() != null ? event.getCreatedBy().toHexString() : null,
                event.getCreatedAt(), event.getUpdatedAt());
    }
}
