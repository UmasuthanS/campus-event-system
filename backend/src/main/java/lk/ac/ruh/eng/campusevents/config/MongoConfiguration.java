package lk.ac.ruh.eng.campusevents.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class MongoConfiguration {
    @Bean
    DateTimeProvider utcDateTimeProvider() {
        // BSON dates have millisecond precision; Instant represents an absolute UTC time.
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
