package lk.ac.ruh.eng.campusevents.dashboard;

import java.time.Instant;
import java.util.List;
import lk.ac.ruh.eng.campusevents.auth.dto.UserResponse;
import lk.ac.ruh.eng.campusevents.club.dto.ClubResponse;

public final class DashboardDtos {
    private DashboardDtos() { }
    public record Counts(long totalEvents, long upcomingEvents, long publishedEvents, long participantRegistrations,
            long pendingApplications, long approvedOrganizers) { }
    public record EventRow(String id, String clubId, String title, String status, Instant startAt,
            long participantRegistrations, long pendingApplications, long approvedOrganizers) { }
    public record Admin(String id, String fullName, String email) { }
    public record ClubRow(ClubResponse club, Counts counts, List<Admin> admins) { }
    public record Summary(Long totalStudents, long totalClubs, long activeClubs, Counts counts,
            List<ClubRow> clubs, List<EventRow> events) { }
    public record Users(List<UserResponse> items, int page, boolean hasNext) { }
}
