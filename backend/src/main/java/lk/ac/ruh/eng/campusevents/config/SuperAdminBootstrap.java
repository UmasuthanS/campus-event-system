package lk.ac.ruh.eng.campusevents.config;

import jakarta.validation.Validator;
import lk.ac.ruh.eng.campusevents.auth.UserIndexes;
import lk.ac.ruh.eng.campusevents.auth.dto.RegisterRequest;
import lk.ac.ruh.eng.campusevents.domain.Role;
import lk.ac.ruh.eng.campusevents.domain.User;
import lk.ac.ruh.eng.campusevents.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.bootstrap.super-admin.enabled", havingValue = "true")
public class SuperAdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final UserIndexes indexes;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final RegisterRequest identity;

    public SuperAdminBootstrap(UserRepository users, UserIndexes indexes, PasswordEncoder passwords, Validator validator,
            @Value("${app.bootstrap.super-admin.full-name:}") String fullName,
            @Value("${app.bootstrap.super-admin.registration-number:}") String registrationNumber,
            @Value("${app.bootstrap.super-admin.email:}") String email,
            @Value("${app.bootstrap.super-admin.password:}") String password) {
        this.users = users;
        this.indexes = indexes;
        this.passwords = passwords;
        this.validator = validator;
        this.identity = new RegisterRequest(fullName, registrationNumber, email, password);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!validator.validate(identity).isEmpty()) {
            throw new IllegalStateException("Super Admin bootstrap requires valid name, registration number, email, and password environment values.");
        }
        indexes.ensure();
        if (existingIdentity()) return;
        User user = new User();
        user.setFullName(identity.fullName());
        user.setRegistrationNumber(identity.registrationNumber());
        user.setEmail(identity.email());
        user.setPasswordHash(passwords.encode(identity.password()));
        user.setRole(Role.SUPER_ADMIN);
        try {
            users.insert(user);
        } catch (DuplicateKeyException exception) {
            // Handle simultaneous starts without overwriting or promoting another account.
            if (!existingIdentity()) throw new IllegalStateException("Super Admin bootstrap identity conflict.");
        }
    }

    private boolean existingIdentity() {
        var byEmail = users.findByEmail(identity.email());
        var byNumber = users.findByRegistrationNumber(identity.registrationNumber());
        if (byEmail.isEmpty() && byNumber.isEmpty()) return false;
        if (byEmail.isPresent() && byNumber.isPresent()
                && byEmail.get().getId().equals(byNumber.get().getId())
                && byEmail.get().getRole() == Role.SUPER_ADMIN) return true;
        throw new IllegalStateException("Super Admin bootstrap identity conflicts with an existing account; no account was changed.");
    }
}
