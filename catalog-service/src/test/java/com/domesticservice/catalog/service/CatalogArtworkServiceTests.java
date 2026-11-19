package com.domesticservice.catalog.service;

import com.domesticservice.catalog.repository.CatalogArtworkRepository;
import com.domesticservice.catalog.repository.CatalogArtworkRepository.Banner;
import com.domesticservice.catalog.service.CatalogArtworkService.BannerInput;
import com.domesticservice.catalog.service.CatalogArtworkService.ImageInput;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogArtworkServiceTests {
    private final CatalogArtworkRepository repository = mock(CatalogArtworkRepository.class);
    private final CatalogAdminService admin = mock(CatalogAdminService.class);
    private final CatalogArtworkService artwork = new CatalogArtworkService(repository, admin);

    @Test
    void imageReplacementAuthorizesAndStoresTheImageThatTheServiceWillRead() throws IOException {
        var inOrder = inOrder(admin, repository);
        when(repository.storeImage(anyString(), any(byte[].class))).thenReturn(7L);
        when(repository.updateServiceImage(4, "/api/v1/catalog/images/7")).thenReturn(true);
        var image = new ImageInput(jpeg(1200, 675));
        assertEquals("/api/v1/catalog/images/7", artwork.replaceServiceImage("admin", 4, image));
        inOrder.verify(admin).admin("admin");
        inOrder.verify(repository).storeImage(matches("[a-f0-9]{64}"), any(byte[].class));
        inOrder.verify(repository).updateServiceImage(4, "/api/v1/catalog/images/7");
    }

    @Test
    void wrongRatioAndUnreadableFileNeverReachTheRepository() throws IOException {
        assertThrows(ResponseStatusException.class, () -> artwork.replaceServiceImage("admin", 4, new ImageInput(jpeg(1200, 500))));
        assertThrows(ResponseStatusException.class, () -> artwork.replaceServiceImage("admin", 4, new ImageInput("data:image/jpeg;base64,YQ==")));
        verifyNoInteractions(repository);
    }

    @Test
    void changingBannerLayoutRequiresTheProperImageWhileTextEditsKeepThePreviousImage() throws IOException {
        when(repository.serviceExists(4)).thenReturn(true);
        when(repository.lockBannerLayout(2)).thenReturn(Optional.of("SPLIT"));
        when(repository.saveBanner(any())).thenAnswer(call -> call.getArgument(0, Banner.class).id());
        when(repository.storeImage(anyString(), any(byte[].class))).thenReturn(9L);
        assertEquals(2L, artwork.saveBanner("admin", 2, banner("SPLIT", "")));
        verify(repository).saveBanner(argThat(value -> value.imageUrl() == null && value.layout().equals("SPLIT")));
        assertThrows(ResponseStatusException.class, () -> artwork.saveBanner("admin", 2, banner("FULL_IMAGE", "")));
        assertEquals(2L, artwork.saveBanner("admin", 2, banner("FULL_IMAGE", jpeg(1200, 500))));
        verify(repository).saveBanner(argThat(value -> "/api/v1/catalog/images/9".equals(value.imageUrl())));
    }

    @Test
    void unauthenticatedWriteNeverReachesStorage() {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED)).when(admin).admin(null);
        assertThrows(ResponseStatusException.class, () -> artwork.saveBanner(null, 0, banner("SPLIT", "")));
        verifyNoInteractions(repository);
    }

    private static BannerInput banner(String layout, String image) {
        return new BannerInput(4, "Tiêu đề", "", "", "", "GREEN", layout, 10, true, image);
    }

    private static String jpeg(int width, int height) throws IOException {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", output));
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
