package com.smartparking.payment.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.payment.api.controller.DisputeController;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.CreateDisputeResponseRequest;
import com.smartparking.payment.application.dto.DisputeDetailDto;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.dto.DisputeEvidenceResponse;
import com.smartparking.payment.application.dto.DisputeTimelineEntryDto;
import com.smartparking.payment.application.dto.DriverDisputeStatus;
import com.smartparking.payment.application.dto.LegacyDisputeDto;
import com.smartparking.payment.application.dto.ResolveDisputeRequest;
import com.smartparking.payment.application.service.DisputeEvidenceService;
import com.smartparking.payment.application.service.DisputeService;
import com.smartparking.payment.domain.model.DisputeHistoryEventType;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeControllerTest {

    @Mock
    private DisputeService disputeService;

    @Mock
    private DisputeEvidenceService disputeEvidenceService;

    @InjectMocks
    private DisputeController controller;

    private AuthenticatedUser driver;
    private AuthenticatedUser merchant;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        driver = new AuthenticatedUser(100L, "driver01", "DRIVER");
        merchant = new AuthenticatedUser(300L, "merchant01", "MERCHANT");
        admin = new AuthenticatedUser(1L, "admin", "ADMIN");
    }

    @Test
    @DisplayName("createDispute returns CREATED status")
    void testCreateDispute() {
        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .bookingId(10L)
                .reason("Fee mismatch")
                .build();
        DisputeDto dto = DisputeDto.builder().id(1L).status(DriverDisputeStatus.OPEN).build();
        when(disputeService.createDispute(100L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<DisputeDto>> res = controller.createDispute(driver, req);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(1L, res.getBody().getData().getId());
        assertEquals(DriverDisputeStatus.OPEN, res.getBody().getData().getStatus());
    }

    @Test
    @DisplayName("getMyDisputes delegates page, size, and status filter to service")
    void testGetMyDisputes() {
        PageResponse<DisputeDto> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(disputeService.getMyDisputes(100L, 0, 10, "OPEN")).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<DisputeDto>>> res =
                controller.getMyDisputes(driver, 0, 10, "OPEN");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(disputeService).getMyDisputes(100L, 0, 10, "OPEN");
    }

    @Test
    @DisplayName("getMyDisputeDetail returns driver detail with timeline")
    void testGetMyDisputeDetail() {
        DisputeDetailDto detail = DisputeDetailDto.builder()
                .id(5L)
                .status(DriverDisputeStatus.IN_REVIEW)
                .timeline(List.of(DisputeTimelineEntryDto.builder()
                        .id(1L)
                        .eventType(DisputeHistoryEventType.MERCHANT_RESPONSE)
                        .build()))
                .build();
        when(disputeService.getMyDisputeDetail(100L, 5L)).thenReturn(detail);

        ResponseEntity<ApiResponse<DisputeDetailDto>> res = controller.getMyDisputeDetail(driver, 5L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(5L, res.getBody().getData().getId());
        assertEquals(DriverDisputeStatus.IN_REVIEW, res.getBody().getData().getStatus());
    }

    @Test
    @DisplayName("addMerchantResponse returns CREATED timeline entry")
    void testAddMerchantResponse() {
        CreateDisputeResponseRequest req = new CreateDisputeResponseRequest("Đã kiểm tra camera bãi xe");
        DisputeTimelineEntryDto entry = DisputeTimelineEntryDto.builder()
                .id(11L)
                .eventType(DisputeHistoryEventType.MERCHANT_RESPONSE)
                .actorRole("MERCHANT")
                .resultingStatus(DriverDisputeStatus.IN_REVIEW)
                .message("Đã kiểm tra camera bãi xe")
                .build();
        when(disputeService.addMerchantResponse(300L, 5L, req)).thenReturn(entry);

        ResponseEntity<ApiResponse<DisputeTimelineEntryDto>> res =
                controller.addMerchantResponse(merchant, 5L, req);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(11L, res.getBody().getData().getId());
        assertEquals(DriverDisputeStatus.IN_REVIEW, res.getBody().getData().getResultingStatus());
    }

    @Test
    @DisplayName("getMerchantDisputes returns legacy merchant disputes")
    void testGetMerchantDisputes() {
        PageResponse<LegacyDisputeDto> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(disputeService.getMerchantDisputes(eq(300L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<LegacyDisputeDto>>> res =
                controller.getMerchantDisputes(merchant, PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @DisplayName("getDisputeDetail passes user role for role-scoped lookup")
    void testGetDisputeDetail() {
        LegacyDisputeDto dto = LegacyDisputeDto.builder().id(5L).status(DisputeStatusEnum.PENDING).build();
        when(disputeService.getDisputeDetail(100L, "ROLE_DRIVER", 5L)).thenReturn(dto);

        ResponseEntity<ApiResponse<LegacyDisputeDto>> res = controller.getDisputeDetail(driver, 5L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(5L, res.getBody().getData().getId());
        assertEquals(DisputeStatusEnum.PENDING, res.getBody().getData().getStatus());
    }

    @Test
    @DisplayName("resolveDispute resolves dispute for Admin")
    void testResolveDispute() {
        ResolveDisputeRequest req = new ResolveDisputeRequest();
        req.setResolution("REFUND_FULL");
        AdminDisputeResponse response = AdminDisputeResponse.builder().id(5L).status(DisputeStatusEnum.RESOLVED).build();
        when(disputeService.resolveDispute(1L, 5L, "REFUND_FULL")).thenReturn(response);

        ResponseEntity<ApiResponse<AdminDisputeResponse>> res = controller.resolveDispute(admin, 5L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(5L, res.getBody().getData().getId());
    }

    @Test
    @DisplayName("adminList and adminDetail")
    void testAdminEndpoints() {
        when(disputeService.getAdminDisputes(any())).thenReturn(new PageResponse<>(new PageImpl<>(Collections.emptyList())));
        when(disputeService.getAdminDispute(5L)).thenReturn(AdminDisputeResponse.builder().id(5L).build());

        ResponseEntity<ApiResponse<PageResponse<AdminDisputeResponse>>> listRes = controller.adminList(PageRequest.of(0, 10));
        assertEquals(HttpStatus.OK, listRes.getStatusCode());

        ResponseEntity<ApiResponse<AdminDisputeResponse>> detailRes = controller.adminDetail(5L);
        assertEquals(HttpStatus.OK, detailRes.getStatusCode());
    }

    @Test
    @DisplayName("uploadEvidence delegates multipart files and returns CREATED")
    void testUploadEvidence() {
        MockMultipartFile file = new MockMultipartFile("files", "receipt.png", "image/png", new byte[]{1, 2, 3});
        List<MultipartFile> files = List.of(file);
        DisputeEvidenceResponse dto = DisputeEvidenceResponse.builder()
                .fileName("11111111-1111-1111-1111-111111111111.png")
                .originalFileName("receipt.png")
                .contentType("image/png")
                .sizeBytes(3L)
                .contentUrl("/api/v1/disputes/evidence/11111111-1111-1111-1111-111111111111.png")
                .url("http://localhost:8088/api/v1/disputes/evidence/11111111-1111-1111-1111-111111111111.png")
                .build();
        when(disputeEvidenceService.upload(eq(100L), eq(files), anyString())).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<DisputeEvidenceResponse>>> res = controller.uploadEvidence(driver, files);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(1, res.getBody().getData().size());
        assertEquals("receipt.png", res.getBody().getData().get(0).getOriginalFileName());
    }

    @Test
    @DisplayName("getEvidenceContent returns inline resource with nosniff and private cache headers")
    void testGetEvidenceContent() {
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(disputeEvidenceService.getContent(300L, "ROLE_MERCHANT", "abc.png"))
                .thenReturn(new DisputeEvidenceService.StoredEvidence(resource, "image/png", "bien-lai.png"));

        ResponseEntity<?> res = controller.getEvidenceContent(merchant, "abc.png");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("nosniff", res.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("inline"));
        assertTrue(res.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL).contains("private"));
    }
}
