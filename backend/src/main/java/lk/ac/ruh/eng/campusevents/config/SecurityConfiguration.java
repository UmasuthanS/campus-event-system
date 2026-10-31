package lk.ac.ruh.eng.campusevents.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Stage 1 exposes only health; authentication will be added in a later stage.
        return http.authorizeHttpRequests(requests -> requests
                .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                .anyRequest().denyAll())
                .build();
    }
}
