package lk.ac.ruh.eng.campusevents.club;

import jakarta.validation.Valid;
import java.util.List;
import lk.ac.ruh.eng.campusevents.club.dto.*;
import lk.ac.ruh.eng.campusevents.domain.ClubStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ClubController {
    private final ClubService clubs;
    public ClubController(ClubService clubs) { this.clubs = clubs; }
    @GetMapping("/clubs")
    public List<ClubResponse> list() { return clubs.activeClubs(); }
    @GetMapping("/clubs/{id}")
    public ClubResponse detail(@PathVariable String id) { return clubs.activeClub(id); }
    @GetMapping("/club-admin/clubs")
    public List<ClubResponse> managedList() { return clubs.managedClubs(); }
    @GetMapping("/club-admin/clubs/{id}")
    public ManagedClubResponse managedDetail(@PathVariable String id) { return clubs.managedClub(id); }
    @PostMapping("/super-admin/clubs")
    public ResponseEntity<ClubResponse> create(@Valid @RequestBody ClubRequest request) { return ResponseEntity.status(201).body(clubs.create(request)); }
    @PutMapping("/club-admin/clubs/{id}")
    public ClubResponse update(@PathVariable String id, @Valid @RequestBody ClubRequest request) { return clubs.update(id, request); }
    @PatchMapping("/super-admin/clubs/{id}/status")
    public ClubResponse status(@PathVariable String id, @Valid @RequestBody ClubStatusRequest request) { return clubs.setStatus(id, request.status()); }
    @DeleteMapping("/super-admin/clubs/{id}")
    public ClubResponse deactivate(@PathVariable String id) { return clubs.setStatus(id, ClubStatus.INACTIVE); }
    @PutMapping("/super-admin/clubs/{id}/admins")
    public ManagedClubResponse assign(@PathVariable String id, @Valid @RequestBody AssignAdminRequest request) { return clubs.assign(id, request); }
    @DeleteMapping("/super-admin/clubs/{id}/admins/{userId}")
    public ManagedClubResponse revoke(@PathVariable String id, @PathVariable String userId) { return clubs.revoke(id, userId); }
}
