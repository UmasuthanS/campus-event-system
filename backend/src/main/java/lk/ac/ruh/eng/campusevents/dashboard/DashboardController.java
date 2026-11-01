package lk.ac.ruh.eng.campusevents.dashboard;

import lk.ac.ruh.eng.campusevents.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static lk.ac.ruh.eng.campusevents.dashboard.DashboardDtos.*;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
    private final DashboardService dashboards;
    public DashboardController(DashboardService dashboards) { this.dashboards = dashboards; }
    @GetMapping("/club-admin/dashboard")
    public Summary clubs() { return dashboards.clubSummary(); }
    @GetMapping("/super-admin/dashboard")
    public Summary platform() { return dashboards.platformSummary(); }
    @GetMapping("/super-admin/users")
    public Users users(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page) {
        if (search.length() > 100 || page < 0 || page > 100000) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Use a search of at most 100 characters and a page between 0 and 100000.");
        return dashboards.directory(search, page);
    }
}
