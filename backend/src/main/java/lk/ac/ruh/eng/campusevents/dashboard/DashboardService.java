package lk.ac.ruh.eng.campusevents.dashboard;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import lk.ac.ruh.eng.campusevents.auth.dto.UserResponse;
import lk.ac.ruh.eng.campusevents.club.dto.ClubResponse;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.repository.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import static lk.ac.ruh.eng.campusevents.dashboard.DashboardDtos.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
public class DashboardService {
    private final ClubRepository clubs;
    private final UserRepository users;
    private final MongoTemplate mongo;
    public DashboardService(ClubRepository clubs, UserRepository users, MongoTemplate mongo) { this.clubs = clubs; this.users = users; this.mongo = mongo; }
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'SUPER_ADMIN')")
    public Summary clubSummary() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean platform = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        return summary(platform ? clubs.findAll() : clubs.findAssigned(new ObjectId(auth.getName())), false);
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Summary platformSummary() { return summary(clubs.findAll(), true); }
    private Summary summary(List<Club> assigned, boolean platform) {
        // Project counts inside MongoDB; never fetch private application text or attendee identities.
        var fields = new Document("clubId", 1).append("title", 1).append("status", 1).append("startsAt", 1)
                .append("participantRegistrations", size("reservations"))
                .append("pendingApplications", applicationCount("PENDING")).append("approvedOrganizers", applicationCount("APPROVED"));
        var grouped = new Document("_id", "$clubId").append("totalEvents", new Document("$sum", 1))
                .append("upcomingEvents", new Document("$sum", new Document("$cond", List.of(new Document("$and", List.of(
                        new Document("$eq", List.of("$status", "PUBLISHED")), new Document("$gt", List.of("$startsAt", Date.from(Instant.now()))))), 1, 0))))
                .append("publishedEvents", new Document("$sum", new Document("$cond", List.of(new Document("$eq", List.of("$status", "PUBLISHED")), 1, 0))))
                .append("participantRegistrations", new Document("$sum", "$participantRegistrations"))
                .append("pendingApplications", new Document("$sum", "$pendingApplications"))
                .append("approvedOrganizers", new Document("$sum", "$approvedOrganizers"));
        if (!platform) grouped.append("events", new Document("$push", "$$ROOT"));
        var scope = platform ? new Criteria() : Criteria.where("clubId").in(assigned.stream().map(Club::getId).toList());
        var groups = mongo.aggregate(newAggregation(match(scope), context -> new Document("$project", fields),
                sort(Sort.Direction.ASC, "startsAt", "_id"), context -> new Document("$group", grouped)), Event.class, Document.class).getMappedResults();
        var stats = new HashMap<ObjectId, Counts>();
        var eventRows = new ArrayList<EventRow>();
        for (Document group : groups) {
            stats.put(group.getObjectId("_id"), counts(group));
            if (!platform) for (Document row : group.getList("events", Document.class, List.of())) {
                eventRows.add(new EventRow(row.getObjectId("_id").toHexString(), row.getObjectId("clubId").toHexString(), row.getString("title"), row.getString("status"),
                        row.getDate("startsAt") == null ? null : row.getDate("startsAt").toInstant(), number(row, "participantRegistrations"), number(row, "pendingApplications"), number(row, "approvedOrganizers")));
            }
        }
        eventRows.sort(Comparator.comparing(EventRow::startAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(EventRow::id));
        // Resolve all assignments together, rather than querying each administrator separately.
        var people = new HashMap<ObjectId, User>();
        if (platform) users.findAllById(assigned.stream().flatMap(c -> adminIds(c).stream()).distinct().toList()).forEach(u -> people.put(u.getId(), u));
        var clubRows = assigned.stream().sorted(Comparator.comparing(Club::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).map(c -> new ClubRow(ClubResponse.from(c),
                stats.getOrDefault(c.getId(), new Counts(0, 0, 0, 0, 0, 0)), platform ? adminIds(c).stream().sorted().map(id -> {
                    User user = people.get(id); return new Admin(id.toHexString(), user == null ? "Deleted account" : user.getFullName(), user == null ? "" : user.getEmail());
                }).toList() : List.<Admin>of())).toList();
        Counts totals = new Counts(groups.stream().mapToLong(g -> number(g, "totalEvents")).sum(), groups.stream().mapToLong(g -> number(g, "upcomingEvents")).sum(),
                groups.stream().mapToLong(g -> number(g, "publishedEvents")).sum(), groups.stream().mapToLong(g -> number(g, "participantRegistrations")).sum(),
                groups.stream().mapToLong(g -> number(g, "pendingApplications")).sum(), groups.stream().mapToLong(g -> number(g, "approvedOrganizers")).sum());
        return new Summary(platform ? mongo.count(Query.query(Criteria.where("role").is(Role.STUDENT)), User.class) : null, assigned.size(),
                assigned.stream().filter(c -> c.getStatus() == ClubStatus.ACTIVE).count(), totals, clubRows, eventRows);
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Users directory(String search, int page) {
        Criteria filter = new Criteria();
        if (search != null && !search.isBlank()) {
            String pattern = Pattern.quote(search.strip());
            filter = new Criteria().orOperator(Criteria.where("fullName").regex(pattern, "i"), Criteria.where("email").regex(pattern, "i"), Criteria.where("registrationNumber").regex(pattern, "i"));
        }
        Query query = Query.query(filter).with(Sort.by("fullName", "_id")).skip((long) page * 20).limit(21);
        query.fields().include("fullName", "registrationNumber", "email", "role");
        var results = mongo.find(query, User.class);
        return new Users(results.stream().limit(20).map(UserResponse::from).toList(), page, results.size() > 20);
    }
    private Set<ObjectId> adminIds(Club club) { return club.getAdminIds() == null ? Set.of() : club.getAdminIds(); }
    private Document size(String field) { return new Document("$size", new Document("$ifNull", List.of("$" + field, List.of()))); }
    private Document applicationCount(String status) {
        return new Document("$size", new Document("$filter", new Document("input", new Document("$ifNull", List.of("$organizerApplications", List.of())))
                .append("as", "application").append("cond", new Document("$eq", List.of("$$application.status", status)))));
    }
    private long number(Document doc, String field) { return ((Number) doc.getOrDefault(field, 0)).longValue(); }
    private Counts counts(Document doc) { return new Counts(number(doc, "totalEvents"), number(doc, "upcomingEvents"), number(doc, "publishedEvents"), number(doc, "participantRegistrations"), number(doc, "pendingApplications"), number(doc, "approvedOrganizers")); }
}
