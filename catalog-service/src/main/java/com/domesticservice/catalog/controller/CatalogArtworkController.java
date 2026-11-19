package com.domesticservice.catalog.controller;

import com.domesticservice.catalog.repository.CatalogArtworkRepository.Banner;
import com.domesticservice.catalog.service.CatalogArtworkService;
import com.domesticservice.catalog.service.CatalogArtworkService.BannerInput;
import com.domesticservice.catalog.service.CatalogArtworkService.ImageInput;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogArtworkController {
    private final CatalogArtworkService artwork;

    public CatalogArtworkController(CatalogArtworkService artwork) { this.artwork = artwork; }

    @GetMapping("/home-banners")
    public List<Banner> banners() { return artwork.publishedBanners(); }

    @GetMapping(value = "/images/{id}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .header("X-Content-Type-Options", "nosniff")
            .body(artwork.image(id));
    }

    @PutMapping("/admin/services/{id}/image")
    public Map<String, String> replaceServiceImage(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token,
                                                   @PathVariable long id, @RequestBody ImageInput input) {
        return Map.of("imageUrl", artwork.replaceServiceImage(token, id, input));
    }

    @GetMapping("/admin/home-banners")
    public List<Banner> adminBanners(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        return artwork.adminBanners(token);
    }

    @PostMapping("/admin/home-banners")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createBanner(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token,
                                           @RequestBody BannerInput input) {
        return Map.of("id", artwork.saveBanner(token, 0, input));
    }

    @PutMapping("/admin/home-banners/{id}")
    public Map<String, Long> updateBanner(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token,
                                           @PathVariable long id, @RequestBody BannerInput input) {
        if (id <= 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy banner.");
        return Map.of("id", artwork.saveBanner(token, id, input));
    }
}
