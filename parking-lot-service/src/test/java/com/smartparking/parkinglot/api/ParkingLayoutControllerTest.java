package com.smartparking.parkinglot.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.parkinglot.api.controller.ParkingLayoutController;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.ParkingLayoutService;
import com.smartparking.parkinglot.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingLayoutControllerTest {

    @Mock
    private ParkingLayoutService parkingLayoutService;

    @InjectMocks
    private ParkingLayoutController controller;

    private AuthenticatedUser merchantUser;
    private final Long parkingLotId = 10L;

    @BeforeEach
    void setUp() {
        merchantUser = new AuthenticatedUser(100L, "merchant01", "MERCHANT");
    }

    @Test
    @DisplayName("uploadSource delegates to service and returns 201 CREATED")
    void testUploadSource() {
        MockMultipartFile file = new MockMultipartFile("file", "layout.png", "image/png", new byte[]{1, 2, 3});
        SourceAssetUploadResponse resp = SourceAssetUploadResponse.builder().sourceAssetId("asset-1").build();
        when(parkingLayoutService.uploadSourceImage(100L, parkingLotId, file)).thenReturn(resp);

        ResponseEntity<ApiResponse<SourceAssetUploadResponse>> response = controller.uploadSource(merchantUser, parkingLotId, file);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("asset-1", response.getBody().getData().getSourceAssetId());
    }

    @Test
    @DisplayName("getDraft delegates to service and returns 200 OK")
    void testGetDraft() {
        LayoutDraftResponse resp = LayoutDraftResponse.builder().layoutVersion(2).build();
        when(parkingLayoutService.getDraft(100L, parkingLotId)).thenReturn(resp);

        ResponseEntity<ApiResponse<LayoutDraftResponse>> response = controller.getDraft(merchantUser, parkingLotId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getData().getLayoutVersion());
    }

    @Test
    @DisplayName("saveDraft delegates to service and returns 200 OK")
    void testSaveDraft() {
        SaveLayoutDraftRequest req = SaveLayoutDraftRequest.builder().expectedVersion(1).build();
        LayoutDraftResponse resp = LayoutDraftResponse.builder().layoutVersion(2).build();
        when(parkingLayoutService.saveDraft(100L, parkingLotId, req)).thenReturn(resp);

        ResponseEntity<ApiResponse<LayoutDraftResponse>> response = controller.saveDraft(merchantUser, parkingLotId, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getData().getLayoutVersion());
    }

    @Test
    @DisplayName("publish delegates to service and returns 200 OK")
    void testPublish() {
        PublishLayoutRequest req = PublishLayoutRequest.builder().expectedVersion(1).build();
        PublishLayoutResponse resp = PublishLayoutResponse.builder().publicationId("pub-101").layoutVersion(1).build();
        when(parkingLayoutService.publishLayout(100L, parkingLotId, req)).thenReturn(resp);

        ResponseEntity<ApiResponse<PublishLayoutResponse>> response = controller.publish(merchantUser, parkingLotId, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("pub-101", response.getBody().getData().getPublicationId());
    }

    @Test
    @DisplayName("activatePhysical delegates to service and returns 200 OK")
    void testActivatePhysical() {
        doNothing().when(parkingLayoutService).activatePhysicalMode(100L, parkingLotId);

        ResponseEntity<ApiResponse<Void>> response = controller.activatePhysical(merchantUser, parkingLotId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(parkingLayoutService).activatePhysicalMode(100L, parkingLotId);
    }

    @Test
    @DisplayName("getSourceContent delegates to service and returns byte array with image/png")
    void testGetSourceContent() {
        byte[] img = new byte[]{10, 20, 30};
        when(parkingLayoutService.getSourceImageContent(100L, parkingLotId, "asset-1")).thenReturn(img);

        ResponseEntity<byte[]> response = controller.getSourceContent(merchantUser, parkingLotId, "asset-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(img, response.getBody());
    }

    @Test
    @DisplayName("getPublicLayout delegates to service and returns 200 OK")
    void testGetPublicLayout() {
        PublicLayoutResponse resp = PublicLayoutResponse.builder().parkingLotId(parkingLotId).publicationId("pub-101").build();
        when(parkingLayoutService.getPublicLayout(parkingLotId)).thenReturn(resp);

        ResponseEntity<ApiResponse<PublicLayoutResponse>> response = controller.getPublicLayout(parkingLotId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("pub-101", response.getBody().getData().getPublicationId());
    }

    @Test
    @DisplayName("getPublicAvailability delegates to service and returns 200 OK")
    void testGetPublicAvailability() {
        PublicAvailabilityResponse resp = PublicAvailabilityResponse.builder()
                .parkingLotId(parkingLotId)
                .slots(Collections.emptyList())
                .build();
        when(parkingLayoutService.getPublicAvailability(parkingLotId)).thenReturn(resp);

        ResponseEntity<ApiResponse<PublicAvailabilityResponse>> response = controller.getPublicAvailability(parkingLotId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(parkingLotId, response.getBody().getData().getParkingLotId());
    }
}
