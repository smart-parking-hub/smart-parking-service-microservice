package com.smartparking.parkinglot.infrastructure.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceImplTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryServiceImpl cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryServiceImpl(cloudinary, "smartparking/lots");
    }

    @Test
    @DisplayName("isAvailable returns true when cloudinary is injected")
    void testIsAvailable() {
        assertTrue(cloudinaryService.isAvailable());

        CloudinaryServiceImpl emptyService = new CloudinaryServiceImpl(null, "smartparking/lots");
        assertFalse(emptyService.isAvailable());
    }

    @Test
    @DisplayName("upload successfully uploads and injects f_auto,q_auto")
    void testUploadSuccess() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "public_id", "smartparking/lots/1/uuid",
                "secure_url", "https://res.cloudinary.com/demo/image/upload/v123456/smartparking/lots/1/uuid.jpg"
        ));

        MockMultipartFile file = new MockMultipartFile("file", "image.jpg", "image/jpeg", "image-content".getBytes());
        var result = cloudinaryService.upload(file, 1L);

        assertEquals("smartparking/lots/1/uuid", result.publicId());
        assertTrue(result.secureUrl().contains("/upload/f_auto,q_auto/"));
    }

    @Test
    @DisplayName("delete executes destroy on uploader")
    void testDeleteSuccess() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("smartparking/lots/1/uuid"), anyMap())).thenReturn(Map.of("result", "ok"));

        assertDoesNotThrow(() -> cloudinaryService.delete("smartparking/lots/1/uuid"));
        verify(uploader, times(1)).destroy(eq("smartparking/lots/1/uuid"), anyMap());
    }
}
