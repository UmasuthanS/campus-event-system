package lk.ac.ruh.eng.campusevents.club;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lk.ac.ruh.eng.campusevents.club.dto.*;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.error.ApiException;
import lk.ac.ruh.eng.campusevents.repository.*;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ClubService {
    private final ClubRepository clubs;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final ClubIndexes indexes;
    public ClubService(ClubRepository clubs, UserRepository users, MongoTemplate mongo, ClubIndexes indexes) {
        this.clubs = clubs; this.users = users; this.mongo = mongo; this.indexes = indexes;
    }
    public List<ClubResponse> activeClubs() { return clubs.findActive().stream().map(ClubResponse::from).toList(); }
    public ClubResponse activeClub(String id) {
        Club club = find(id);
        if (club.getStatus() != ClubStatus.ACTIVE) throw notFound();
        return ClubResponse.from(club);
    }
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'SUPER_ADMIN')")
    public List<ClubResponse> managedClubs() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean platformAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        return (platformAdmin ? clubs.findAll() : clubs.findAssigned(new ObjectId(auth.getName())))
                .stream().map(ClubResponse::from).toList();
    }
    @PreAuthorize("@clubAuthorization.canView(authentication, #id)")
    public ManagedClubResponse managedClub(String id) { return managed(find(id)); }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ClubResponse create(@Valid ClubRequest request) {
        indexes.ensure();
        Club club = new Club();
        apply(club, request);
        return save(club);
    }
    @PreAuthorize("@clubAuthorization.canManage(authentication, #id)")
    public ClubResponse update(String id, @Valid ClubRequest request) {
        indexes.ensure();
        Club club = find(id);
        apply(club, request);
        // Update profile fields only: concurrent status or assignment changes must survive.
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            Criteria permission = Criteria.where("_id").is(club.getId());
            if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
                permission.and("adminIds").is(new ObjectId(auth.getName())).and("status").ne(ClubStatus.INACTIVE);
            }
            var result = mongo.updateFirst(Query.query(permission), new Update()
                    .set("name", club.getName()).set("shortName", club.getShortName()).set("code", club.getShortName())
                    .set("nameKey", club.getNameKey()).set("shortNameKey", club.getShortNameKey())
                    .set("description", club.getDescription()).set("logoUrl", club.getLogoUrl())
                    .set("contactEmail", club.getContactEmail()).set("updatedAt", Instant.now()), Club.class);
            if (result.getMatchedCount() == 0) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Club edit access is no longer available.");
            return ClubResponse.from(find(id));
        } catch (DuplicateKeyException exception) { throw duplicate(); }
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ClubResponse setStatus(String id, ClubStatus status) {
        Club club = find(id);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(club.getId())),
                new Update().set("status", status).set("updatedAt", Instant.now()), Club.class);
        return ClubResponse.from(find(id));
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ManagedClubResponse assign(String id, @Valid AssignAdminRequest request) {
        Club club = find(id);
        if (club.getStatus() != ClubStatus.ACTIVE) throw new ApiException(HttpStatus.CONFLICT, "INACTIVE_CLUB", "Reactivate this club before assigning admins.");
        User user = users.findByEmail(request.email()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "No account matches that email."));
        if (user.getRole() == Role.SUPER_ADMIN) throw new ApiException(HttpStatus.CONFLICT, "PLATFORM_ADMIN", "Super Admins already have access to every club.");
        if (user.getRole() != Role.STUDENT && user.getRole() != Role.CLUB_ADMIN) throw new ApiException(HttpStatus.CONFLICT, "INVALID_ROLE", "Account cannot be assigned.");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId()).and("role").is(Role.STUDENT)),
                new Update().set("role", Role.CLUB_ADMIN).set("updatedAt", Instant.now()), User.class);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(club.getId())),
                new Update().addToSet("adminIds", user.getId()).set("updatedAt", Instant.now()), Club.class);
        return managed(find(id));
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ManagedClubResponse revoke(String id, String userId) {
        Club club = find(id);
        if (!ObjectId.isValid(userId)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "Invalid user ID.");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(club.getId())),
                new Update().pull("adminIds", new ObjectId(userId)).set("updatedAt", Instant.now()), Club.class);
        return managed(find(id));
    }
    private ClubResponse save(Club club) {
        try { return ClubResponse.from(clubs.insert(club)); }
        catch (DuplicateKeyException exception) { throw duplicate(); }
    }
    private void apply(Club club, ClubRequest request) {
        club.setName(request.name()); club.setShortName(request.shortName());
        club.setNameKey(ClubIndexes.key(request.name())); club.setShortNameKey(ClubIndexes.key(request.shortName()));
        club.setDescription(request.description()); club.setLogoUrl(request.logoUrl()); club.setContactEmail(request.contactEmail());
    }
    private ManagedClubResponse managed(Club club) {
        var admins = (club.getAdminIds() == null ? java.util.Set.<ObjectId>of() : club.getAdminIds()).stream()
                .map(id -> users.findById(id).map(user -> new ManagedClubResponse.AdminSummary(id.toHexString(), user.getFullName(), user.getEmail()))
                        .orElse(new ManagedClubResponse.AdminSummary(id.toHexString(), "Deleted account", ""))).toList();
        return new ManagedClubResponse(ClubResponse.from(club), admins);
    }
    private Club find(String id) {
        if (id == null || !ObjectId.isValid(id)) throw notFound();
        return clubs.findById(new ObjectId(id)).orElseThrow(ClubService::notFound);
    }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "CLUB_NOT_FOUND", "Club not found."); }
    private static ApiException duplicate() { return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CLUB", "Club name or short name is already in use."); }
}
