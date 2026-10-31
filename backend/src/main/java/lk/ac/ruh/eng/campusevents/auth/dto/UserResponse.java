package lk.ac.ruh.eng.campusevents.auth.dto;

import lk.ac.ruh.eng.campusevents.domain.Role;
import lk.ac.ruh.eng.campusevents.domain.User;

public record UserResponse(String id, String fullName, String registrationNumber, String email, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId().toHexString(), user.getFullName(),
                user.getRegistrationNumber(), user.getEmail(), user.getRole());
    }
}
