package lk.ac.ruh.eng.campusevents.authorization;

import lk.ac.ruh.eng.campusevents.repository.ClubRepository;
import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("clubAuthorization")
public class ClubAuthorization {
    private final ClubRepository clubs;

    public ClubAuthorization(ClubRepository clubs) { this.clubs = clubs; }

    public boolean canManage(Authentication authentication, String clubId) { return check(authentication, clubId, true); }
    public boolean canView(Authentication authentication, String clubId) { return check(authentication, clubId, false); }

    private boolean check(Authentication authentication, String clubId, boolean editing) {
        if (authentication == null || !authentication.isAuthenticated() || clubId == null
                || !ObjectId.isValid(clubId) || !ObjectId.isValid(authentication.getName())) return false;
        boolean superAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        boolean clubAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLUB_ADMIN"));
        if (!superAdmin && !clubAdmin) return false;
        return clubs.findById(new ObjectId(clubId)).map(club -> superAdmin
                || ((!editing || club.getStatus() == lk.ac.ruh.eng.campusevents.domain.ClubStatus.ACTIVE)
                && club.getAdminIds() != null && club.getAdminIds().contains(new ObjectId(authentication.getName())))).orElse(false);
    }
}
