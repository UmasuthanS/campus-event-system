package lk.ac.ruh.eng.campusevents.club.dto;

import java.time.Instant;
import lk.ac.ruh.eng.campusevents.domain.Club;
import lk.ac.ruh.eng.campusevents.domain.ClubStatus;

public record ClubResponse(String id, String name, String shortName, String description, String logoUrl,
                           String contactEmail, ClubStatus status, Instant createdAt, Instant updatedAt) {
    public static ClubResponse from(Club club) {
        return new ClubResponse(club.getId().toHexString(), club.getName(), club.getShortName(), club.getDescription(),
                club.getLogoUrl(), club.getContactEmail(), club.getStatus(), club.getCreatedAt(), club.getUpdatedAt());
    }
}
