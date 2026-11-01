package lk.ac.ruh.eng.campusevents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import lk.ac.ruh.eng.campusevents.config.DevelopmentClubSeeder;
import lk.ac.ruh.eng.campusevents.config.SuperAdminBootstrap;
import lk.ac.ruh.eng.campusevents.config.DevelopmentClubAdminSeeder;

import static org.assertj.core.api.Assertions.assertThat;

class SeedProfileTest {
    @Test
    void seederCannotRunOutsideDevelopmentEvenWhenEnabled() {
        new ApplicationContextRunner().withUserConfiguration(DevelopmentClubSeeder.class)
                .withPropertyValues("spring.profiles.active=test", "app.seed.clubs.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DevelopmentClubSeeder.class));
    }
    @Test
    void superAdminBootstrapCannotRunOutsideDevelopment() {
        new ApplicationContextRunner().withUserConfiguration(SuperAdminBootstrap.class)
                .withPropertyValues("spring.profiles.active=test", "app.bootstrap.super-admin.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(SuperAdminBootstrap.class));
    }

    @Test
    void superAdminBootstrapIsDisabledByDefaultInDevelopment() {
        new ApplicationContextRunner().withUserConfiguration(SuperAdminBootstrap.class)
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).doesNotHaveBean(SuperAdminBootstrap.class));
    }
    @Test
    void demoAdminsCannotBeSeededOutsideDevelopment() {
        new ApplicationContextRunner().withUserConfiguration(DevelopmentClubAdminSeeder.class)
                .withPropertyValues("spring.profiles.active=test", "app.seed.club-admins.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DevelopmentClubAdminSeeder.class));
    }
    @Test
    void demoAdminSeedIsDisabledByDefaultInDevelopment() {
        new ApplicationContextRunner().withUserConfiguration(DevelopmentClubAdminSeeder.class)
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).doesNotHaveBean(DevelopmentClubAdminSeeder.class));
    }
}
