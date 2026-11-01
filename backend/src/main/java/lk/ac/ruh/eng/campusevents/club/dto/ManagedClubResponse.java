package lk.ac.ruh.eng.campusevents.club.dto;

import java.util.List;

public record ManagedClubResponse(ClubResponse club, List<AdminSummary> admins) {
    public record AdminSummary(String id, String fullName, String email) { }
}
