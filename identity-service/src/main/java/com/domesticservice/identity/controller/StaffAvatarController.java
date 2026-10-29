package com.domesticservice.identity.controller;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class StaffAvatarController {

    private static final byte[] AVATAR = avatar();

    private static byte[] avatar() {
        try {
            var picture = new BufferedImage(192, 192, BufferedImage.TYPE_INT_RGB);
            var pen = picture.createGraphics();
            pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            pen.setColor(new Color(220, 252, 231));
            pen.fillRect(0, 0, 192, 192);
            pen.setColor(new Color(5, 150, 105));
            pen.fillOval(60, 31, 72, 72);
            pen.fillRoundRect(29, 113, 134, 105, 70, 70);
            pen.dispose();
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(picture, "png", bytes);
            return bytes.toByteArray();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot create staff avatar", ex);
        }
    }

    @GetMapping(value = {"/api/v1/identity/staff-avatar.png", "/api/v1/identity/default-avatar.png"}, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> get(jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        if (request.getRequestURI().endsWith("/default-avatar.png")) {
            try (var input = getClass().getResourceAsStream("/default-avatar.png")) {
                return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1))).body(input.readAllBytes());
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1))).body(AVATAR);
    }
}
