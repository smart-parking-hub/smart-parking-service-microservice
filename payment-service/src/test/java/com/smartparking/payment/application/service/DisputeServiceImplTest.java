package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.service.impl.DisputeServiceImpl;
import com.smartparking.payment.domain.model.Dispute;
import com.smartparking.payment.domain.repository.DisputeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceImplTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private com.smartparking.payment.domain.repository.PaymentRepository paymentRepository;

    @Mock
    private com.smartparking.payment.application.service.MerchantWalletService merchantWalletService;

    @InjectMocks
    private DisputeServiceImpl disputeService;

    private final Long driverUserId = 200L;
    private final Long merchantUserId = 300L;
    private final Long disputeId = 15L;

    @Test
    @DisplayName("Create dispute fails when booking already has an active dispute")
    void testCreateDisputeConflict() {
        Dispute existing = Dispute.builder().id(1L).bookingId(10L).build();
        when(disputeRepository.findByBookingId(10L)).thenReturn(Optional.of(existing));

        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .bookingId(10L)
                .merchantId(merchantUserId)
                .reason("Double charged")
                .build();

        AppException ex = assertThrows(AppException.class, () -> disputeService.createDispute(driverUserId, req));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Create dispute successfully saves dispute in PENDING status")
    void testCreateDisputeSuccess() {
        when(disputeRepository.findByBookingId(10L)).thenReturn(Optional.empty());

        Dispute saved = Dispute.builder()
                .id(disputeId)
                .bookingId(10L)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .reason("Double charged")
                .description("Card charged twice")
                .status(DisputeStatusEnum.PENDING)
                .build();
        when(disputeRepository.save(any(Dispute.class))).thenReturn(saved);

        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .bookingId(10L)
                .merchantId(merchantUserId)
                .reason("Double charged")
                .description("Card charged twice")
                .build();

        DisputeDto dto = disputeService.createDispute(driverUserId, req);

        assertNotNull(dto);
        assertEquals(DisputeStatusEnum.PENDING, dto.getStatus());
        assertEquals("Double charged", dto.getReason());
    }

    @Test
    @DisplayName("Get dispute detail throws FORBIDDEN when user has no permission")
    void testGetDisputeDetailForbidden() {
        Dispute dispute = Dispute.builder()
                .id(disputeId)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        AppException ex = assertThrows(AppException.class, () -> disputeService.getDisputeDetail(9999L, disputeId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get dispute detail returns dispute when requested by merchant")
    void testGetDisputeDetailMerchantAccess() {
        Dispute dispute = Dispute.builder()
                .id(disputeId)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        DisputeDto dto = disputeService.getDisputeDetail(merchantUserId, disputeId);
        assertNotNull(dto);
        assertEquals(disputeId, dto.getId());
    }

    @Test
    @DisplayName("Get merchant disputes returns page of disputes")
    void testGetMerchantDisputes() {
        Dispute d = Dispute.builder().id(2L).merchantId(merchantUserId).build();
        when(disputeRepository.findByMerchantIdOrderByCreatedAtDesc(eq(merchantUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 10), 1));

        PageResponse<DisputeDto> page = disputeService.getMerchantDisputes(merchantUserId, PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
    }

    @Test
    @DisplayName("Admin resolves dispute successfully")
    void testResolveDisputeSuccess() {
        Dispute dispute = Dispute.builder()
                .id(disputeId)
                .status(DisputeStatusEnum.PENDING)
                .build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminDisputeResponse response = disputeService.resolveDispute(1L, disputeId, "Hoàn trả 50.000đ cho khách");

        assertNotNull(response);
        assertEquals(DisputeStatusEnum.RESOLVED, response.getStatus());
        assertEquals("Hoàn trả 50.000đ cho khách", response.getResolution());
    }

    @Test
    @DisplayName("Admin resolve dispute throws NOT_FOUND when dispute does not exist")
    void testResolveDisputeNotFound() {
        when(disputeRepository.findById(999L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                disputeService.resolveDispute(1L, 999L, "Resolution"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get admin disputes returns page of disputes")
    void testGetAdminDisputes() {
        Dispute d = Dispute.builder().id(3L).build();
        when(disputeRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 10), 1));

        PageResponse<AdminDisputeResponse> page = disputeService.getAdminDisputes(PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
    }

    @Test
    @DisplayName("Get admin dispute returns detail when dispute exists")
    void testGetAdminDisputeSuccess() {
        Dispute d = Dispute.builder().id(disputeId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(d));

        AdminDisputeResponse res = disputeService.getAdminDispute(disputeId);
        assertNotNull(res);
        assertEquals(disputeId, res.getId());
    }

    @Test
    @DisplayName("Get admin dispute throws NOT_FOUND when dispute does not exist")
    void testGetAdminDisputeNotFound() {
        when(disputeRepository.findById(404L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                disputeService.getAdminDispute(404L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get my disputes returns page of disputes")
    void testGetMyDisputes() {
        Dispute d = Dispute.builder().id(1L).driverId(driverUserId).build();
        when(disputeRepository.findByDriverIdOrderByCreatedAtDesc(eq(driverUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 10), 1));

        PageResponse<DisputeDto> page = disputeService.getMyDisputes(driverUserId, PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
    }
}
