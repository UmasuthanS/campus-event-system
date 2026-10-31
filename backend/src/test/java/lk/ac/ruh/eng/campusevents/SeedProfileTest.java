package lk.ac.ruh.eng.campusevents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import lk.ac.ruh.eng.campusevents.config.DevelopmentClubSeeder;

import static org.assertj.core.api.Assertions.assertThat;

class SeedProfileTest {
    @Test
    void seederCannotRunOutsideDevelopmentEvenWhenEnabled() {
        new ApplicationContextRunner().withUserConfiguration(DevelopmentClubSeeder.class)
                .withPropertyValues("spring.profiles.active=test", "app.seed.clubs.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DevelopmentClubSeeder.class));
    }
}
