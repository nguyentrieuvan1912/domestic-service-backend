package com.domesticservice.identity.service;

import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AvatarService {
    public static final String DEFAULT = "/api/v1/identity/default-avatar.png";
    private final JdbcTemplate db;
    private final CloudinaryClient cloud;
    public AvatarService(JdbcTemplate db, CloudinaryClient cloud) { this.db = db; this.cloud = cloud; }

    @Transactional
    public void save(long id, String data, boolean required) {
        if (data == null || data.isBlank()) {
            if (required) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin cần chọn ảnh chân dung rõ mặt, chính chủ của nhân viên.");
            db.update("UPDATE users SET avatar_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", DEFAULT, id);
            return;
        }
        // An unchanged reference is allowed only for an image already belonging to this account.
        String current = db.queryForObject("SELECT avatar_url FROM users WHERE id=?", String.class, id);
        if (data.equals(current) && data.startsWith("https://res.cloudinary.com/")) return;
        if (data.length() > 4200000 || !data.matches("(?s)data:image/(jpeg|png);base64,[A-Za-z0-9+/=]+"))
            throw invalid();
        try {
            byte[] bytes = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
            if (bytes.length > 3 * 1024 * 1024) throw invalid();
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid();
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 32 || height < 32 || width > 8000 || height > 8000 || (long) width * height > 24000000) throw invalid();
                    var image = reader.read(0);
                    // Decode before sending to Cloudinary; never accept an arbitrary client URL.
                    if (image == null) throw invalid();
                    String publicId = "cleanmaster/avatars/" + id + "/" + UUID.randomUUID();
                    String url = cloud.uploadAvatar(data, publicId);
                    db.update("UPDATE users SET avatar_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", url, id);
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException ex) { throw invalid(); }
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ảnh JPG hoặc PNG hợp lệ, tối đa 3 MB, kích thước từ 32 px đến 8000 px (tối đa 24 triệu điểm ảnh)."); }
}
