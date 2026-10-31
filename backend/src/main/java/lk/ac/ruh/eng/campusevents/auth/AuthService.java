package lk.ac.ruh.eng.campusevents.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lk.ac.ruh.eng.campusevents.auth.dto.*;
import lk.ac.ruh.eng.campusevents.domain.Role;
import lk.ac.ruh.eng.campusevents.domain.User;
import lk.ac.ruh.eng.campusevents.error.ApiException;
import lk.ac.ruh.eng.campusevents.repository.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final UserIndexes indexes;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt, UserIndexes indexes) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.indexes = indexes;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public AuthResponse register(RegisterRequest request) {
        indexes.ensure();
        if (users.findByEmail(request.email()).isPresent() || users.findByRegistrationNumber(request.registrationNumber()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_USER", "Email or registration number is already registered.");
        }
        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setRegistrationNumber(request.registrationNumber());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(Role.STUDENT);
        return jwt.issue(users.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email()).orElse(null);
        String hash = user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash();
        boolean valid = request.password().getBytes(StandardCharsets.UTF_8).length <= 72 && passwords.matches(request.password(), hash);
        if (!valid || user == null || user.getRole() == null) throw invalidCredentials();
        return jwt.issue(user);
    }

    public UserResponse currentUser(String subject) {
        if (subject == null || !ObjectId.isValid(subject)) throw invalidCredentials();
        User user = users.findById(new ObjectId(subject)).orElseThrow(AuthService::invalidCredentials);
        if (user.getRole() == null) throw invalidCredentials();
        return UserResponse.from(user);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email, password, or authentication token.");
    }
}
