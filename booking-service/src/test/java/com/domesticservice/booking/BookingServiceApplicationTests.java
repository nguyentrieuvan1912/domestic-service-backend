package com.domesticservice.booking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingServiceApplicationTests {
    @LocalServerPort int port;

    @Test
    void scaffoldEndpointAndDatabaseHealthAreAvailable() throws Exception {
        var client = HttpClient.newHttpClient();
        var info = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/booking/system/info")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(info.statusCode()).isEqualTo(200);
        assertThat(info.body()).contains("booking-service", "scaffold");
        var health = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/actuator/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("UP");
    }
}
