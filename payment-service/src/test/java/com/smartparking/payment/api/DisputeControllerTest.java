package com.smartparking.payment.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.api.controller.DisputeController;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.dto.ResolveDisputeRequest;
import com.smartparking.payment.application.service.DisputeService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeControllerTest {

    @Mock
    private DisputeService disputeService;

    @InjectMocks
    private DisputeController controller;

    private AuthenticatedUser user;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(100L, "driver01", "DRIVER");
        admin = new AuthenticatedUser(1L, "admin", "ADMIN");
    }

    @Test
    @DisplayName("createDispute returns CREATED status")
    void testCreateDispute() {
        CreateDisputeRequest req = new CreateDisputeRequest();
        DisputeDto dto = DisputeDto.builder().id(1L).build();
        when(disputeService.createDispute(100L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<DisputeDto>> res = controller.createDispute(user, req);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(1L, res.getBody().getData().getId());
    }

    @Test
    @DisplayName("getMyDisputes returns driver disputes")
    void testGetMyDisputes() {
        PageResponse<DisputeDto> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(disputeService.getMyDisputes(eq(100L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<DisputeDto>>> res = controller.getMyDisputes(user, PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @DisplayName("getMerchantDisputes returns merchant disputes")
    void testGetMerchantDisputes() {
        PageResponse<DisputeDto> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(disputeService.getMerchantDisputes(eq(100L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<DisputeDto>>> res = controller.getMerchantDisputes(user, PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @DisplayName("getDisputeDetail returns detail")
    void testGetDisputeDetail() {
        DisputeDto dto = DisputeDto.builder().id(5L).build();
        when(disputeService.getDisputeDetail(100L, 5L)).thenReturn(dto);

        ResponseEntity<ApiResponse<DisputeDto>> res = controller.getDisputeDetail(user, 5L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(5L, res.getBody().getData().getId());
    }

    @Test
    @DisplayName("resolveDispute resolves dispute for Admin")
    void testResolveDispute() {
        ResolveDisputeRequest req = new ResolveDisputeRequest();
        req.setResolution("REFUND_FULL");
        AdminDisputeResponse response = AdminDisputeResponse.builder().id(5L).build();
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
}
