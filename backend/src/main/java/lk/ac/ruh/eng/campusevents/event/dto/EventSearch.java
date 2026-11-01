package lk.ac.ruh.eng.campusevents.event.dto;

import java.time.Instant;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

public record EventSearch(@Size(max = 120) String search, @Pattern(regexp = "[0-9a-fA-F]{24}") String clubId,
        @Size(max = 80) String category, @Pattern(regexp = "all|upcoming|past") String period,
        @Pattern(regexp = "asc|desc") String sort,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) { }
