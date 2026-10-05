package domesticservice_api_gateway;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {
    static HttpServer upstream;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void upstreamUrl(DynamicPropertyRegistry registry) throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        upstream.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().getPath().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        upstream.start();
        String url = "http://localhost:" + upstream.getAddress().getPort();
        for (String name : new String[]{"IDENTITY_SERVICE_URL", "CATALOG_SERVICE_URL",
                "BOOKING_SERVICE_URL", "FINANCE_SERVICE_URL", "AI_SERVICE_URL"}) {
            registry.add(name, () -> url);
        }
    }

    @AfterAll
    static void stopUpstream() { if (upstream != null) upstream.stop(0); }

    @Test
    void allRoutesPreserveApiPathsAndUnmatchedPathsReturn404() throws Exception {
        var client = HttpClient.newHttpClient();
        for (String service : new String[]{"identity", "catalog", "booking", "finance", "ai-assistant"}) {
            String path = "/api/v1/" + service + "/system/info";
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(path);
        }
        var missing = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/unknown")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(404);
    }

    @Test
    void corsAllowsConfiguredWebOriginAndRejectsUnknownOrigin() throws Exception {
        var client = HttpClient.newHttpClient();
        for (String origin : new String[]{"http://localhost:5173", "https://untrusted.example"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                    + "/api/v1/catalog/system/info"))
                    .header("Origin", origin).header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "Idempotency-Key")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (origin.contains("5173")) {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(origin);
                assertThat(response.headers().firstValue("Access-Control-Allow-Headers"))
                        .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase("Idempotency-Key"));
            } else {
                assertThat(response.statusCode()).isEqualTo(403);
            }
        }
    }
}
