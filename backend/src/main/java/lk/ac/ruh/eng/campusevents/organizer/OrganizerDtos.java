package lk.ac.ruh.eng.campusevents.organizer;

import java.time.Instant;
import jakarta.validation.constraints.*;
import lk.ac.ruh.eng.campusevents.domain.ApplicationStatus;
import lk.ac.ruh.eng.campusevents.event.dto.EventResponse;

public final class OrganizerDtos {
    private OrganizerDtos() { }
    public record Apply(@NotBlank @Size(max = 100) String preferredRole,
            @NotBlank @Size(max = 2000) String motivation, @NotNull @Size(max = 2000) String experience) { }
    public record TeamMember(String fullName, String preferredRole) { }
    public record Review(@NotNull ApplicationStatus status) { }
    public record Response(String id, String studentId, String eventId, String preferredRole, String motivation,
            String experience, ApplicationStatus status, Instant createdAt, Instant reviewedAt, String reviewedBy,
            boolean canWithdraw, String fullName, String registrationNumber, EventResponse event) { }
}
