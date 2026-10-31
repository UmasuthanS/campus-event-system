package lk.ac.ruh.eng.campusevents;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.mongodb.uri=mongodb://127.0.0.1:1/health_test?serverSelectionTimeoutMS=100&connectTimeoutMS=100")
class HealthEndpointTest {
    @Autowired
    private TestRestTemplate client;

    @Test
    void healthIsPublicEvenWhenMongoIsUnavailable() {
        var response = client.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void otherRoutesRemainDenied() {
        var response = client.getForEntity("/api/v1/events", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
