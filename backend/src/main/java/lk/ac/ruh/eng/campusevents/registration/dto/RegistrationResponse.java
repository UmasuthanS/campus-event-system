package lk.ac.ruh.eng.campusevents.registration.dto;

import java.time.Instant;
import lk.ac.ruh.eng.campusevents.domain.RegistrationStatus;
import lk.ac.ruh.eng.campusevents.event.dto.EventResponse;

public record RegistrationResponse(String id, String eventId, String studentId, RegistrationStatus status,
        Instant createdAt, Instant cancelledAt, boolean canCancel, EventResponse event) { }
