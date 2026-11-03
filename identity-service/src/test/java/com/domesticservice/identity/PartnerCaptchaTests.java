package com.domesticservice.identity;

import com.domesticservice.identity.repository.PartnerCaptchaRepository;
import com.domesticservice.identity.service.PartnerCaptchaService;
import com.domesticservice.identity.service.RegistrationVerificationService;
import java.time.Instant;
import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="spring.datasource.url=jdbc:h2:mem:partnercaptcha;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class PartnerCaptchaTests {
    @Autowired PartnerCaptchaService captcha;
    @Autowired PartnerCaptchaRepository repository;
    @Autowired JdbcTemplate db;
    @LocalServerPort int port;
    @BeforeEach void schema() throws Exception {
        db.execute("DROP TABLE IF EXISTS partner_captchas");
        db.execute("DROP TABLE IF EXISTS partner_request_budgets");
        try (var connection = Objects.requireNonNull(db.getDataSource()).getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("accounts-schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V17__partner_captcha.sql"));
        }
    }
    String known(Instant expiry) {
        String id = UUID.randomUUID().toString();
        repository.create(id, RegistrationVerificationService.digest(id + ":ABC234"), expiry);
        return id;
    }
    @Test void generatedImageContainsPngButNoPlaintextAnswer() throws Exception {
        var challenge = captcha.issue();
        assertTrue(challenge.image().startsWith("data:image/png;base64,"));
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(challenge.image().split(",")[1])));
        assertEquals(240, image.getWidth());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM partner_captchas WHERE id=?", Integer.class, challenge.id()));
        assertTrue(challenge.expiresAt().isAfter(Instant.now()));
    }
    @Test void correctAnswerIsCaseInsensitiveAndCanOnlyBeUsedOnce() {
        String id = known(Instant.now().plusSeconds(60));
        assertDoesNotThrow(() -> captcha.verify(id, "abc234"));
        assertThrows(ResponseStatusException.class, () -> captcha.verify(id, "ABC234"));
    }
    @Test void incorrectExpiredMissingAndReplayedAnswersAreRejected() {
        String id = known(Instant.now().plusSeconds(60));
        assertThrows(ResponseStatusException.class, () -> captcha.verify(id, "XXXXXX"));
        assertThrows(ResponseStatusException.class, () -> captcha.verify(id, "ABC234"));
        String expired = known(Instant.now().minusSeconds(1));
        assertThrows(ResponseStatusException.class, () -> captcha.verify(expired, "ABC234"));
        assertThrows(ResponseStatusException.class, () -> captcha.verify(null, null));
    }
    @Test void concurrentRequestsCannotReuseTheSameChallenge() {
        String id = known(Instant.now().plusSeconds(60));
        var calls = java.util.stream.IntStream.range(0, 2).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
            try { captcha.verify(id, "ABC234"); return 1; } catch (ResponseStatusException ex) { return 0; }
        })).toList();
        assertEquals(1, calls.stream().mapToInt(CompletableFuture::join).sum());
    }
    @Test void budgetsApplyToInvalidRequestsAndSurviveSeparateTransactions() {
        for (int i = 0; i < 30; i++) assertThrows(ResponseStatusException.class, () -> captcha.verify(null, null));
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> captcha.verify(null, null)).getStatusCode().value());
        for (int i = 0; i < 120; i++) repository.takeBudget("captcha", 120);
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> captcha.issue()).getStatusCode().value());
        db.update("UPDATE partner_request_budgets SET window_started_at=?", java.sql.Timestamp.from(Instant.now().minusSeconds(61)));
        assertDoesNotThrow(() -> captcha.issue());
    }
    @Test void publicApiRejectsMissingCaptchaBeforeAttemptingToSaveCandidate() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/identity/candidates/apply"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("CAPTCHA"));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM staff_candidates", Integer.class));
    }
    @Test void validPublicSubmissionPersistsExactlyOneCandidateAndRejectsReplay() throws Exception {
        String id = known(Instant.now().plusSeconds(60));
        String body = """
                {"fullName":"Partner test","email":"partner@test.invalid","phone":"0901234567",
                 "identityCard":"123456789012","birthYear":2000,"gender":"Nam","area":"Test",
                 "services":["Cleaning"],"captchaId":"%s","captchaCode":"ABC234"}
                """.formatted(id);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/identity/candidates/apply"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var client = HttpClient.newHttpClient();
        assertEquals(201, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(400, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM staff_candidates", Integer.class));
    }
}
