package com.smartparking.parkinglot.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.parkinglot.api.controller.ParkingLotImageController;
import com.smartparking.parkinglot.application.dto.ParkingLotImageResponse;
import com.smartparking.parkinglot.application.service.ParkingLotImageService;
import com.smartparking.parkinglot.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingLotImageControllerTest {

    @Mock
    private ParkingLotImageService imageService;

    @InjectMocks
    private ParkingLotImageController controller;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(100L, "merchant01", "MERCHANT");
    }

    @Test
    @DisplayName("upload images returns CREATED status")
    void testUpload() {
        MockMultipartFile file = new MockMultipartFile("files", "test.jpg", "image/jpeg", "image".getBytes());
        ParkingLotImageResponse resp = ParkingLotImageResponse.builder().id(1L).build();
        when(imageService.upload(100L, 10L, List.of(file))).thenReturn(List.of(resp));

        ResponseEntity<ApiResponse<List<ParkingLotImageResponse>>> response =
                controller.upload(user, 10L, List.of(file));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    @DisplayName("list returns images for lot")
    void testList() {
        when(imageService.getImages(10L)).thenReturn(List.of(ParkingLotImageResponse.builder().id(1L).build()));

        ResponseEntity<ApiResponse<List<ParkingLotImageResponse>>> response = controller.list(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    @DisplayName("content returns image Resource with appropriate headers")
    void testContent() {
        Resource res = new ByteArrayResource("data".getBytes());
        ParkingLotImageService.StoredImage stored = new ParkingLotImageService.StoredImage(res, "image/jpeg", "test.jpg");
        when(imageService.getContent(10L, 1L)).thenReturn(stored);

        ResponseEntity<Resource> response = controller.content(10L, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("delete removes image and returns success")
    void testDelete() {
        ResponseEntity<ApiResponse<Void>> response = controller.delete(user, 10L, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(imageService, times(1)).delete(100L, 10L, 1L);
    }

    @Test
    @DisplayName("setCover marks image as cover")
    void testSetCover() {
        ParkingLotImageResponse resp = ParkingLotImageResponse.builder().id(1L).cover(true).build();
        when(imageService.setCover(100L, 10L, 1L)).thenReturn(resp);

        ResponseEntity<ApiResponse<ParkingLotImageResponse>> response = controller.setCover(user, 10L, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isCover());
    }
}
