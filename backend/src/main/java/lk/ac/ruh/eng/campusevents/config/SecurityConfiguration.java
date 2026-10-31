package lk.ac.ruh.eng.campusevents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lk.ac.ruh.eng.campusevents.error.ApiError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> "STUDENT".equals(jwt.getClaimAsString("role"))
                ? List.of(new SimpleGrantedAuthority("ROLE_STUDENT")) : List.of());
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasRole("STUDENT")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(mapper, response, 401))
                        .accessDeniedHandler((request, response, exception) -> writeError(mapper, response, 403)))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, exception) -> writeError(mapper, response, 401))
                        .accessDeniedHandler((request, response, exception) -> writeError(mapper, response, 403)))
                .build();
    }

    private void writeError(ObjectMapper mapper, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(response.getOutputStream(), new ApiError(status,
                status == 401 ? "UNAUTHORIZED" : "FORBIDDEN",
                status == 401 ? "Authentication is required or the token is invalid." : "Access is denied."));
    }
}
