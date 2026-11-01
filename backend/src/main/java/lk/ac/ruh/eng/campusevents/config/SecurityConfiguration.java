package lk.ac.ruh.eng.campusevents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lk.ac.ruh.eng.campusevents.error.ApiError;
import lk.ac.ruh.eng.campusevents.repository.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper, UserRepository users) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            try {
                var user = users.findById(new ObjectId(jwt.getSubject()))
                        .orElseThrow(() -> new BadCredentialsException("Account no longer exists."));
                // Role changes invalidate old privileges immediately, including old student tokens after promotion.
                return user.getRole() != null && user.getRole().name().equals(jwt.getClaimAsString("role"))
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())) : List.of();
            } catch (DataAccessException exception) {
                throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"), "Authentication service unavailable.", exception);
            }
        });
        return http
                // Tokens are sent only in Authorization headers, never automatically in cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/health/database").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*", "/api/v1/clubs", "/api/v1/clubs/*", "/api/v1/clubs/*/events").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clubs/*/events").hasAnyRole("CLUB_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/events/*", "/api/v1/events/*/status").hasAnyRole("CLUB_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registrations").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/registrations").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/registrations/*").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/registrations").hasAnyRole("CLUB_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasAnyRole("STUDENT", "CLUB_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/club-admin/**").hasAnyRole("CLUB_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/super-admin/**").hasRole("SUPER_ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> authenticationError(mapper, response, exception))
                        .accessDeniedHandler((request, response, exception) -> writeError(mapper, response, 403)))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, exception) -> authenticationError(mapper, response, exception))
                        .accessDeniedHandler((request, response, exception) -> writeError(mapper, response, 403)))
                .build();
    }

    private void authenticationError(ObjectMapper mapper, HttpServletResponse response, AuthenticationException exception) throws IOException {
        boolean unavailable = exception instanceof OAuth2AuthenticationException oauth
                && "temporarily_unavailable".equals(oauth.getError().getErrorCode());
        writeError(mapper, response, unavailable ? 503 : 401);
    }

    private void writeError(ObjectMapper mapper, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(response.getOutputStream(), new ApiError(status,
                status == 503 ? "DATABASE_UNAVAILABLE" : status == 401 ? "UNAUTHORIZED" : "FORBIDDEN",
                status == 503 ? "Service temporarily unavailable. Please try again."
                        : status == 401 ? "Authentication is required or the token is invalid." : "Access is denied."));
    }
}
