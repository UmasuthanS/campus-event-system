package lk.ac.ruh.eng.campusevents.auth.dto;

import java.util.Locale;
import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                           @NotBlank @Size(max = 72) String password) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    @Override
    public String toString() { return "LoginRequest[redacted]"; }
}
