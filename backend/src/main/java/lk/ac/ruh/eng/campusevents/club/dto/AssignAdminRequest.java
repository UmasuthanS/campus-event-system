package lk.ac.ruh.eng.campusevents.club.dto;

import jakarta.validation.constraints.*;

public record AssignAdminRequest(@NotBlank @Email @Size(max = 254) String email) {
    public AssignAdminRequest { email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT); }
}
