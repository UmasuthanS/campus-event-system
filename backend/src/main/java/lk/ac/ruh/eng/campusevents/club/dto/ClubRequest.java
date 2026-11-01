package lk.ac.ruh.eng.campusevents.club.dto;

import java.net.URI;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

public record ClubRequest(@NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9 &._-]{1,23}", message = "Use 2–24 letters, numbers, spaces, &, ., _, or -.") String shortName,
        @NotBlank @Size(max = 4000) String description,
        @Size(max = 2048) String logoUrl,
        @Email @Size(max = 254) String contactEmail) {
    public ClubRequest {
        name = name == null ? null : name.strip().replaceAll("\\s+", " ");
        shortName = shortName == null ? null : shortName.strip().replaceAll("\\s+", " ");
        description = description == null ? null : description.strip();
        logoUrl = logoUrl == null ? "" : logoUrl.strip();
        contactEmail = contactEmail == null ? "" : contactEmail.strip().toLowerCase(java.util.Locale.ROOT);
    }
    @JsonIgnore
    @AssertTrue(message = "Use an absolute http or https logo URL without embedded credentials.")
    public boolean isLogoUrlValid() {
        if (logoUrl.isEmpty()) return true;
        try {
            URI uri = URI.create(logoUrl);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) { return false; }
    }
}
