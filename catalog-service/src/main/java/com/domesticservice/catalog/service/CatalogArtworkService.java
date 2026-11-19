package com.domesticservice.catalog.service;

import com.domesticservice.catalog.repository.CatalogArtworkRepository;
import com.domesticservice.catalog.repository.CatalogArtworkRepository.Banner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogArtworkService {
    public record ImageInput(String imageData) {}
    public record BannerInput(long serviceId, String title, String badge, String description,
                              String buttonLabel, String theme, String layout, int sortOrder, boolean active, String imageData) {}

    private static final String DATA_PREFIX = "data:image/jpeg;base64,";
    private static final int MAX_BYTES = 1_572_864;
    private static final Set<String> THEMES = Set.of("BLUE", "GREEN", "ORANGE", "NAVY");
    private final CatalogArtworkRepository artwork;
    private final CatalogAdminService admin;

    public CatalogArtworkService(CatalogArtworkRepository artwork, CatalogAdminService admin) {
        this.artwork = artwork;
        this.admin = admin;
    }

    public List<Banner> publishedBanners() { return artwork.banners(true); }

    public List<Banner> adminBanners(String token) {
        admin.admin(token);
        return artwork.banners(false);
    }

    public byte[] image(long id) {
        return artwork.image(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh."));
    }

    @Transactional
    public String replaceServiceImage(String token, long id, ImageInput input) {
        admin.admin(token);
        String url = storeImage(input.imageData(), 1200, 675);
        if (!artwork.updateServiceImage(id, url)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dịch vụ.");
        return url;
    }

    @Transactional
    public long saveBanner(String token, long id, BannerInput input) {
        admin.admin(token);
        if (!artwork.serviceExists(input.serviceId())) throw bad("Vui lòng chọn dịch vụ liên kết.");
        boolean fullImage = "FULL_IMAGE".equals(input.layout());
        if (!fullImage && !"SPLIT".equals(input.layout())) throw bad("Kiểu hiển thị banner không hợp lệ.");
        if (input.theme() == null || !THEMES.contains(input.theme())) throw bad("Màu banner không hợp lệ.");
        if (input.sortOrder() < 0 || input.sortOrder() > 9999) throw bad("Thứ tự hiển thị phải từ 0 đến 9999.");
        String title = text(input.title(), 80, true);
        String badge = text(input.badge(), 30, false);
        String description = text(input.description(), 140, false);
        String buttonLabel = text(input.buttonLabel(), 24, false);
        String priorLayout = id == 0 ? "" : artwork.lockBannerLayout(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy banner."));
        String url = null;
        if (input.imageData() != null && !input.imageData().isBlank()) {
            url = fullImage ? storeImage(input.imageData(), 1200, 500) : storeImage(input.imageData(), 660, 768);
        } else if (!priorLayout.equals(input.layout())) {
            throw bad("Vui lòng chọn ảnh phù hợp với kiểu hiển thị banner.");
        }
        return artwork.saveBanner(new Banner(id, input.serviceId(), title, badge, description, buttonLabel,
            input.theme(), input.layout(), url, input.sortOrder(), input.active()));
    }

    private String storeImage(String data, int width, int height) {
        if (data == null || !data.startsWith(DATA_PREFIX) || data.length() > DATA_PREFIX.length() + 4 * ((MAX_BYTES + 2) / 3))
            throw bad("Vui lòng chọn ảnh hợp lệ, tối đa 1,5 MB sau khi xử lý.");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data.substring(DATA_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw bad("Không đọc được tệp ảnh.");
        }
        if (bytes.length > MAX_BYTES) throw bad("Ảnh vượt quá 1,5 MB sau khi xử lý.");
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReadersByFormatName("JPEG");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                if (reader.getWidth(0) != width || reader.getHeight(0) != height)
                    throw bad("Ảnh phải có kích thước " + width + " × " + height + " px.");
                reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw bad("Không đọc được tệp ảnh JPEG.");
        }
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return "/api/v1/catalog/images/" + artwork.storeImage(hash, bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String text(String value, int max, boolean required) {
        String cleaned = value == null ? "" : value.strip();
        if ((required && cleaned.isBlank()) || cleaned.length() > max) throw bad("Vui lòng kiểm tra nội dung banner.");
        return cleaned;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
