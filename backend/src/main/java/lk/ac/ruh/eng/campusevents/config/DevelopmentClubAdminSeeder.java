package lk.ac.ruh.eng.campusevents.config;

import jakarta.validation.Validator;
import java.time.Instant;
import java.util.*;
import lk.ac.ruh.eng.campusevents.auth.UserIndexes;
import lk.ac.ruh.eng.campusevents.auth.dto.RegisterRequest;
import lk.ac.ruh.eng.campusevents.domain.*;
import lk.ac.ruh.eng.campusevents.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(2)
@ConditionalOnProperty(name = "app.seed.club-admins.enabled", havingValue = "true")
public class DevelopmentClubAdminSeeder implements ApplicationRunner {
    private final Environment environment;
    private final Validator validator;
    private final UserRepository users;
    private final ClubRepository clubs;
    private final UserIndexes indexes;
    private final PasswordEncoder passwords;
    private final MongoTemplate mongo;

    public DevelopmentClubAdminSeeder(Environment environment, Validator validator, UserRepository users,
            ClubRepository clubs, UserIndexes indexes, PasswordEncoder passwords, MongoTemplate mongo) {
        this.environment = environment; this.validator = validator; this.users = users;
        this.clubs = clubs; this.indexes = indexes; this.passwords = passwords; this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var plans = new ArrayList<Plan>();
        var emails = new HashSet<String>();
        var numbers = new HashSet<String>();
        for (String code : List.of("IEEE", "ComES", "AIESEC")) {
            String prefix = "app.seed.club-admins." + code.toLowerCase(Locale.ROOT) + ".";
            RegisterRequest identity = new RegisterRequest(value(prefix + "name"), value(prefix + "registration-number"),
                    value(prefix + "email"), value(prefix + "password"));
            if (identity.fullName().isBlank() && identity.registrationNumber().isBlank()
                    && identity.email().isBlank() && identity.password().isBlank()) continue;
            if (!validator.validate(identity).isEmpty()) throw new IllegalStateException("Valid environment credentials are required for demo " + code + " admin.");
            if (!emails.add(identity.email()) || !numbers.add(identity.registrationNumber())) {
                throw new IllegalStateException("Demo admin identities must be distinct.");
            }
            Club club = clubs.findByCode(code).orElseThrow(() -> new IllegalStateException("Seed the initial clubs before demo admin accounts."));
            if (club.getStatus() != ClubStatus.ACTIVE) throw new IllegalStateException("Demo admin seed requires an active " + code + " club.");
            plans.add(new Plan(club, identity));
            existing(identity); // Preflight all identities before creating any accounts.
        }
        if (plans.isEmpty()) throw new IllegalStateException("Configure credentials for at least one demo club admin.");
        indexes.ensure();
        for (Plan plan : plans) {
            RegisterRequest identity = plan.identity();
            if (existing(identity)) continue; // Never reset credentials or restore an explicitly revoked assignment.
            User user = new User();
            user.setFullName(identity.fullName()); user.setRegistrationNumber(identity.registrationNumber());
            user.setEmail(identity.email()); user.setPasswordHash(passwords.encode(identity.password())); user.setRole(Role.CLUB_ADMIN);
            try { users.insert(user); }
            catch (DuplicateKeyException exception) {
                if (existing(identity)) continue;
                throw new IllegalStateException("Demo admin identity conflict.");
            }
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(plan.club().getId()).and("status").ne(ClubStatus.INACTIVE)),
                    new Update().addToSet("adminIds", user.getId()).set("updatedAt", Instant.now()), Club.class);
            if (result.getMatchedCount() != 1) throw new IllegalStateException("Demo club is no longer active; assign access through Super Admin management.");
        }
    }

    private String value(String property) { return environment.getProperty(property, ""); }
    private boolean existing(RegisterRequest identity) {
        var email = users.findByEmail(identity.email());
        var number = users.findByRegistrationNumber(identity.registrationNumber());
        if (email.isEmpty() && number.isEmpty()) return false;
        if (email.isPresent() && number.isPresent() && email.get().getId().equals(number.get().getId())
                && email.get().getRole() == Role.CLUB_ADMIN) return true;
        throw new IllegalStateException("Demo admin identity conflicts with an existing account; no account was promoted or overwritten.");
    }
    private record Plan(Club club, RegisterRequest identity) { }
}
