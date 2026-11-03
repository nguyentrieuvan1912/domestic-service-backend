package com.domesticservice.identity.service;

import com.domesticservice.identity.repository.PartnerCaptchaRepository;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import static com.domesticservice.identity.service.AccountService.error;

@Service
public class PartnerCaptchaService {
    private final PartnerCaptchaRepository repository;
    private final SecureRandom random = new SecureRandom();
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public record Challenge(String id, String image, Instant expiresAt) {}
    public PartnerCaptchaService(PartnerCaptchaRepository repository) { this.repository = repository; }

    public Challenge issue() {
        repository.takeBudget("captcha", 120);
        String id = UUID.randomUUID().toString();
        var code = new StringBuilder();
        for (int i = 0; i < 6; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        String image = render(code.toString());
        Instant expiry = Instant.now().plusSeconds(300);
        repository.create(id, RegistrationVerificationService.digest(id + ":" + code), expiry);
        return new Challenge(id, image, expiry);
    }

    public void verify(Object challengeId, Object answer) {
        repository.takeBudget("application", 30);
        if (!(challengeId instanceof String id) || !id.matches("[a-f0-9-]{36}")
                || !(answer instanceof String code) || !code.strip().matches("[a-zA-Z0-9]{6}"))
            throw error(HttpStatus.BAD_REQUEST, "Vui lòng nhập mã CAPTCHA gồm 6 ký tự trong ảnh.");
        if (!repository.consume(id, RegistrationVerificationService.digest(id + ":" + code.strip().toUpperCase(Locale.ROOT))))
            throw error(HttpStatus.BAD_REQUEST, "CAPTCHA không đúng, đã hết hạn hoặc đã sử dụng. Vui lòng lấy mã mới.");
    }

    private String render(String code) {
        var image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(240, 253, 250)); g.fillRect(0, 0, 240, 80);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < 12; i++) {
                g.setColor(new Color(140 + random.nextInt(70), 170 + random.nextInt(50), 170 + random.nextInt(50)));
                g.drawLine(random.nextInt(240), random.nextInt(80), random.nextInt(240), random.nextInt(80));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            for (int i = 0; i < code.length(); i++) {
                var transform = g.getTransform();
                g.translate(18 + i * 36, 48 + random.nextInt(12));
                g.rotate((random.nextDouble() - 0.5) * 0.4);
                g.setColor(new Color(20 + random.nextInt(55), 60 + random.nextInt(50), 60 + random.nextInt(50)));
                g.drawString(String.valueOf(code.charAt(i)), 0, 0); g.setTransform(transform);
            }
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot render CAPTCHA", ex); }
        finally { g.dispose(); }
    }
}
