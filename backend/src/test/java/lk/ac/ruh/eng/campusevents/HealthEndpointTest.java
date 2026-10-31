package lk.ac.ruh.eng.campusevents;

import org.junit.jupiter.api.Test;
import lk.ac.ruh.eng.campusevents.auth.JwtService;
import lk.ac.ruh.eng.campusevents.domain.User;
import org.bson.types.ObjectId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.data.mongodb.uri=mongodb://127.0.0.1:1/health_test?serverSelectionTimeoutMS=100&connectTimeoutMS=100",
                "spring.data.mongodb.auto-index-creation=false",
                "app.seed.clubs.enabled=false"
        })
class HealthEndpointTest {
    @Autowired
    private TestRestTemplate client;
    @Autowired private JwtService jwt;

    @Test
    void healthIsPublicEvenWhenMongoIsUnavailable() {
        var response = client.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void databaseHealthReportsUnavailableWithoutLeakingDetails() {
        var response = client.getForEntity("/api/v1/health/database", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"DOWN\"}");
    }

    @Test
    void otherRoutesRemainDenied() {
        var response = client.getForEntity("/api/v1/private-resource", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test
    void authenticatedDatabaseFailureReturns503WithoutLeakingDetails() {
        User user = new User();
        user.setId(new ObjectId());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.issue(user).accessToken());
        var response = client.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("DATABASE_UNAVAILABLE").doesNotContain("mongodb", "27017");
    }
}
