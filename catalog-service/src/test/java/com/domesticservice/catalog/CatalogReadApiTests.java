package com.domesticservice.catalog;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql("/catalog-read-fixture.sql")
class CatalogReadApiTests {
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = new JsonMapper();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/catalog" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void categoriesExcludeInactive() throws Exception {
        var response = get("/categories");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).size()).isEqualTo(2);
        assertThat(response.body()).doesNotContain("HIDDEN");
    }

    @Test void listIsPagedAndExcludesInactiveServicesAndParents() throws Exception {
        var first = json.readTree(get("/services?size=1").body());
        assertThat(first.get("total").asLong()).isEqualTo(2);
        assertThat(first.get("totalPages").asLong()).isEqualTo(2);
        assertThat(first.get("items").size()).isEqualTo(1);
        assertThat(first.get("items").get(0).get("id").isIntegralNumber()).isTrue();
        var next = json.readTree(get("/services?size=1&page=1").body());
        assertThat(next.get("items").get(0).get("id").asLong())
                .isNotEqualTo(first.get("items").get(0).get("id").asLong());
        assertThat(json.readTree(get("/services?page=99").body()).get("items").size()).isZero();
    }

    @Test void filtersAndSearchCombineWithoutWildcardOrSqlInjection() throws Exception {
        String q = URLEncoder.encode(" DỌN ", StandardCharsets.UTF_8);
        var filtered = json.readTree(get("/services?categoryId=1&q=" + q).body());
        assertThat(filtered.get("total").asLong()).isEqualTo(1);
        assertThat(filtered.get("items").get(0).get("id").asLong()).isEqualTo(1);
        for (String search : new String[]{"%", "_", "' OR 1=1 --", "unknown"}) {
            assertThat(json.readTree(get("/services?q=" + URLEncoder.encode(search, StandardCharsets.UTF_8))
                    .body()).get("total").asLong()).isZero();
        }
        assertThat(json.readTree(get("/services?categoryId=2").body()).get("total").asLong()).isZero();
    }

    @Test void detailContainsOnlyActiveOwnedChildrenAndStructuredRequirements() throws Exception {
        var response = get("/services/1");
        assertThat(response.statusCode()).isEqualTo(200);
        var detail = json.readTree(response.body());
        assertThat(detail.get("service").get("basePrice").asLong()).isEqualTo(180000);
        assertThat(detail.get("packages").size()).isEqualTo(1);
        assertThat(detail.get("addOns").size()).isEqualTo(1);
        assertThat(detail.get("requirements").size()).isEqualTo(1);
        assertThat(detail.get("requirements").get(0).get("validationRules").get("max").asInt()).isEqualTo(200);
        assertThat(detail.get("workflow").size()).isEqualTo(2);
        assertThat(detail.get("benefits").size()).isEqualTo(1);
        assertThat(response.body()).doesNotContain("Hidden", "customerId", "quote", "version");
    }

    @Test void unknownInactiveAndInactiveCategoryDetailsAre404Problems() throws Exception {
        for (int id : new int[]{2, 3, 999}) {
            var response = get("/services/" + id);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
            assertThat(json.readTree(response.body()).get("status").asInt()).isEqualTo(404);
        }
    }

    @Test void invalidParametersAre400() throws Exception {
        for (String path : new String[]{"/services?page=-1", "/services?size=0", "/services?size=101",
                "/services?categoryId=-1", "/services?categoryId=abc", "/services/0", "/services/abc",
                "/services?q=" + "x".repeat(101), "/services?page=100001"}) {
            assertThat(get(path).statusCode()).as(path).isEqualTo(400);
        }
    }
}
