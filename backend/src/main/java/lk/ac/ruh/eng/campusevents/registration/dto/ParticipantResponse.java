package lk.ac.ruh.eng.campusevents.registration.dto;

import java.time.Instant;
import lk.ac.ruh.eng.campusevents.domain.RegistrationStatus;

public record ParticipantResponse(String id, String studentId, String fullName, String registrationNumber,
        RegistrationStatus status, Instant createdAt, Instant cancelledAt) { }
