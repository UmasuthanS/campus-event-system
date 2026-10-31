package lk.ac.ruh.eng.campusevents.auth.dto;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Pattern(regexp = "EG/\\d{4}/\\d{3,4}", message = "Use EG/YYYY/NNN or EG/YYYY/NNNN.") String registrationNumber,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72)
        @Pattern(regexp = "(?s)^(?=.*[A-Za-z])(?=.*[0-9]).+$", message = "Include at least one letter and one number.") String password) {
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        registrationNumber = registrationNumber == null ? null : registrationNumber.strip().toUpperCase(Locale.ROOT);
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @JsonIgnore
    @AssertTrue(message = "Password must use at most 72 UTF-8 bytes.")
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    @Override
    public String toString() { return "RegisterRequest[redacted]"; }
}
