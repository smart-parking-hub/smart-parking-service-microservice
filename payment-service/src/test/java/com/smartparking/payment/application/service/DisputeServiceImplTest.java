package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.messaging.TransactionalEventOutbox;
import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.CreateDisputeResponseRequest;
import com.smartparking.payment.application.dto.DisputeDetailDto;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.dto.DisputeTimelineEntryDto;
import com.smartparking.payment.application.dto.DriverDisputeStatus;
import com.smartparking.payment.application.dto.LegacyDisputeDto;
import com.smartparking.payment.application.service.impl.DisputeServiceImpl;
import com.smartparking.payment.application.service.impl.DisputeWriteService;
import com.smartparking.payment.domain.model.Dispute;
import com.smartparking.payment.domain.model.DisputeEvidenceFile;
import com.smartparking.payment.domain.model.DisputeHistory;
import com.smartparking.payment.domain.model.DisputeHistoryEventType;
import com.smartparking.payment.domain.repository.DisputeEvidenceFileRepository;
import com.smartparking.payment.domain.repository.DisputeHistoryRepository;
import com.smartparking.payment.domain.repository.DisputeRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.infrastructure.client.DisputeBookingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceImplTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeHistoryRepository disputeHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MerchantWalletService merchantWalletService;

    @Mock
    private DisputeBookingClient disputeBookingClient;

    @Mock
    private DisputeWriteService disputeWriteService;

    @InjectMocks
    private DisputeServiceImpl disputeService;

    private final Long driverUserId = 200L;
    private final Long merchantUserId = 300L;
    private final Long parkingLotId = 88L;
    private final Long disputeId = 15L;

    @Test
    @DisplayName("Create dispute uses verified booking merchant and parkingLot while ignoring client merchantId")
    void testCreateDisputeSuccess() {
        DisputeBookingClient.VerifiedBooking verified =
                new DisputeBookingClient.VerifiedBooking(10L, driverUserId, merchantUserId, parkingLotId);
        when(disputeBookingClient.verifyBookingOwnership(10L, driverUserId)).thenReturn(verified);

        List<String> urls = List.of("https://cdn.example.com/e1.jpg");
        Dispute saved = Dispute.builder()
                .id(disputeId)
                .bookingId(10L)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .parkingLotId(parkingLotId)
                .reason("Double charged")
                .description("Card charged twice")
                .evidenceUrls(urls)
                .status(DisputeStatusEnum.PENDING)
                .build();
        when(disputeWriteService.createDisputeRecord(
                driverUserId, 10L, merchantUserId, parkingLotId, "Double charged", "Card charged twice", urls))
                .thenReturn(saved);

        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .bookingId(10L)
                .merchantId(99999L)
                .parkingLotId(parkingLotId)
                .reason(" Double charged ")
                .description("Card charged twice")
                .evidenceUrls(urls)
                .build();

        DisputeDto dto = disputeService.createDispute(driverUserId, req);

        assertNotNull(dto);
        assertEquals(DriverDisputeStatus.OPEN, dto.getStatus());
        assertEquals(merchantUserId, dto.getMerchantId());
        assertEquals(parkingLotId, dto.getParkingLotId());
        assertEquals("Double charged", dto.getReason());
        assertEquals(urls, dto.getEvidenceUrls());
    }

    @Test
    @DisplayName("Create dispute rejects mismatched parkingLotId with BAD_REQUEST without persisting")
    void testCreateDisputeParkingLotMismatch() {
        when(disputeBookingClient.verifyBookingOwnership(10L, driverUserId)).thenReturn(
                new DisputeBookingClient.VerifiedBooking(10L, driverUserId, merchantUserId, parkingLotId));

        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .bookingId(10L)
                .parkingLotId(999L)
                .reason("Wrong lot")
                .build();

        AppException ex = assertThrows(AppException.class, () -> disputeService.createDispute(driverUserId, req));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verifyNoInteractions(disputeWriteService);
    }

    @Test
    @DisplayName("DisputeWriteService blocks duplicate booking across any status via pre-check and constraint race fallback")
    void testWriteServiceConflictAndConstraintTranslation() {
        DisputeRepository repo = mock(DisputeRepository.class);
        DisputeEvidenceFileRepository evidenceRepo = mock(DisputeEvidenceFileRepository.class);
        DisputeWriteService writeService = new DisputeWriteService(repo, evidenceRepo, mock(TransactionalEventOutbox.class));

        when(repo.findByBookingId(10L)).thenReturn(Optional.of(Dispute.builder()
                .id(1L).bookingId(10L).status(DisputeStatusEnum.RESOLVED).build()));

        AppException preCheck = assertThrows(AppException.class, () ->
                writeService.createDisputeRecord(driverUserId, 10L, merchantUserId, parkingLotId, "Reason", null, List.of()));
        assertEquals(ErrorCode.CONFLICT, preCheck.getErrorCode());

        when(repo.findByBookingId(20L)).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(Dispute.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException("Duplicate entry '20' for key 'disputes.uk_disputes_booking_id'")));

        AppException raceConflict = assertThrows(AppException.class, () ->
                writeService.createDisputeRecord(driverUserId, 20L, merchantUserId, parkingLotId, "Reason", null, List.of()));
        assertEquals(ErrorCode.CONFLICT, raceConflict.getErrorCode());

        when(repo.findByBookingId(30L)).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(Dispute.class))).thenThrow(
                new DataIntegrityViolationException("Column 'reason' cannot be null"));
        assertThrows(DataIntegrityViolationException.class, () ->
                writeService.createDisputeRecord(driverUserId, 30L, merchantUserId, parkingLotId, "Reason", null, List.of()));
    }

    @Test
    @DisplayName("DisputeWriteService links uploaded evidence files exclusively to owner driver and rejects stolen/reused/missing files")
    void testWriteServiceLinksEvidenceFilesAndEnforcesOwnership() {
        DisputeRepository repo = mock(DisputeRepository.class);
        DisputeEvidenceFileRepository evidenceRepo = mock(DisputeEvidenceFileRepository.class);
        DisputeWriteService writeService = new DisputeWriteService(repo, evidenceRepo, mock(TransactionalEventOutbox.class));

        String fileUuid1 = "11111111-1111-1111-1111-111111111111.png";
        String internalUrl1 = "http://localhost:8088/api/v1/disputes/evidence/" + fileUuid1;

        DisputeEvidenceFile ownedFile = DisputeEvidenceFile.builder()
                .id(1L)
                .storedFileName(fileUuid1)
                .originalFileName("receipt.png")
                .contentType("image/png")
                .sizeBytes(1024L)
                .uploaderDriverId(driverUserId)
                .disputeId(null)
                .build();

        when(repo.findByBookingId(40L)).thenReturn(Optional.empty());
        when(evidenceRepo.findByStoredFileNameInForUpdate(List.of(fileUuid1))).thenReturn(List.of(ownedFile));
        when(repo.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            d.setId(99L);
            return d;
        });

        Dispute saved = writeService.createDisputeRecord(
                driverUserId, 40L, merchantUserId, parkingLotId, "Reason", "Desc", List.of(internalUrl1));
        assertEquals(99L, saved.getId());
        assertEquals(99L, ownedFile.getDisputeId());
        verify(evidenceRepo).saveAllAndFlush(List.of(ownedFile));

        // Missing file -> 400 BAD_REQUEST
        String missingUuid = "22222222-2222-2222-2222-222222222222.png";
        when(repo.findByBookingId(41L)).thenReturn(Optional.empty());
        when(evidenceRepo.findByStoredFileNameInForUpdate(List.of(missingUuid))).thenReturn(List.of());
        AppException missingEx = assertThrows(AppException.class, () -> writeService.createDisputeRecord(
                driverUserId, 41L, merchantUserId, parkingLotId, "Reason", null,
                List.of("http://localhost:8088/api/v1/disputes/evidence/" + missingUuid)));
        assertEquals(ErrorCode.BAD_REQUEST, missingEx.getErrorCode());

        // Another driver's uploaded file -> 403 FORBIDDEN
        DisputeEvidenceFile foreignFile = DisputeEvidenceFile.builder()
                .id(2L)
                .storedFileName(fileUuid1)
                .uploaderDriverId(999L)
                .disputeId(null)
                .build();
        when(repo.findByBookingId(42L)).thenReturn(Optional.empty());
        when(evidenceRepo.findByStoredFileNameInForUpdate(List.of(fileUuid1))).thenReturn(List.of(foreignFile));
        AppException forbiddenEx = assertThrows(AppException.class, () -> writeService.createDisputeRecord(
                driverUserId, 42L, merchantUserId, parkingLotId, "Reason", null, List.of(internalUrl1)));
        assertEquals(ErrorCode.FORBIDDEN, forbiddenEx.getErrorCode());

        // Already attached to another dispute -> 409 CONFLICT
        DisputeEvidenceFile reusedFile = DisputeEvidenceFile.builder()
                .id(3L)
                .storedFileName(fileUuid1)
                .uploaderDriverId(driverUserId)
                .disputeId(77L)
                .build();
        when(repo.findByBookingId(43L)).thenReturn(Optional.empty());
        when(evidenceRepo.findByStoredFileNameInForUpdate(List.of(fileUuid1))).thenReturn(List.of(reusedFile));
        AppException conflictEx = assertThrows(AppException.class, () -> writeService.createDisputeRecord(
                driverUserId, 43L, merchantUserId, parkingLotId, "Reason", null, List.of(internalUrl1)));
        assertEquals(ErrorCode.CONFLICT, conflictEx.getErrorCode());
    }

    @Test
    @DisplayName("Get my disputes filters by status, batches IN_REVIEW lookup, and clamps out-of-range page/size")
    void testGetMyDisputesFiltersAndBatchMapping() {
        Dispute open = Dispute.builder().id(1L).driverId(driverUserId).status(DisputeStatusEnum.PENDING).build();
        Dispute inReview = Dispute.builder().id(2L).driverId(driverUserId).status(DisputeStatusEnum.PENDING).build();
        Dispute resolved = Dispute.builder().id(3L).driverId(driverUserId).status(DisputeStatusEnum.RESOLVED).build();
        Dispute rejected = Dispute.builder().id(4L).driverId(driverUserId).status(DisputeStatusEnum.REJECTED).build();

        when(disputeRepository.findAllDriverDisputesOrdered(eq(driverUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(open, inReview, resolved, rejected), PageRequest.of(0, 20), 4));
        when(disputeHistoryRepository.findDisputeIdsWithEventType(
                eq(List.of(1L, 2L)), eq(DisputeHistoryEventType.MERCHANT_RESPONSE)))
                .thenReturn(Set.of(2L));

        PageResponse<DisputeDto> page = disputeService.getMyDisputes(driverUserId, -1, 500, null);

        assertEquals(4, page.getContent().size());
        assertEquals(DriverDisputeStatus.OPEN, page.getContent().get(0).getStatus());
        assertEquals(DriverDisputeStatus.IN_REVIEW, page.getContent().get(1).getStatus());
        assertEquals(DriverDisputeStatus.RESOLVED, page.getContent().get(2).getStatus());
        assertEquals(DriverDisputeStatus.REJECTED, page.getContent().get(3).getStatus());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(disputeRepository).findAllDriverDisputesOrdered(eq(driverUserId), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
        assertTrue(pageableCaptor.getValue().getSort().isUnsorted());

        when(disputeRepository.findDriverInReviewDisputesOrdered(eq(driverUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inReview)));
        when(disputeHistoryRepository.findDisputeIdsWithEventType(
                eq(List.of(2L)), eq(DisputeHistoryEventType.MERCHANT_RESPONSE)))
                .thenReturn(Set.of(2L));

        PageResponse<DisputeDto> filtered = disputeService.getMyDisputes(driverUserId, 0, 10, "IN_REVIEW");
        assertEquals(1, filtered.getContent().size());
        assertEquals(DriverDisputeStatus.IN_REVIEW, filtered.getContent().get(0).getStatus());
    }

    @Test
    @DisplayName("Get my disputes rejects invalid or blank status filters with BAD_REQUEST")
    void testGetMyDisputesInvalidStatus() {
        for (String bad : List.of("", " ", "open", "PENDING", "UNKNOWN")) {
            AppException ex = assertThrows(AppException.class,
                    () -> disputeService.getMyDisputes(driverUserId, 0, 10, bad));
            assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        }
        verifyNoInteractions(disputeRepository);
    }

    @Test
    @DisplayName("Get my dispute detail binds ownership in query and returns chronological timeline")
    void testGetMyDisputeDetail() {
        Dispute dispute = Dispute.builder()
                .id(disputeId)
                .bookingId(10L)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .parkingLotId(parkingLotId)
                .reason("Sai phi")
                .evidenceUrls(List.of("https://cdn.example.com/1.png"))
                .status(DisputeStatusEnum.PENDING)
                .build();
        when(disputeRepository.findByIdAndDriverId(disputeId, driverUserId)).thenReturn(Optional.of(dispute));
        when(disputeHistoryRepository.findByDisputeIdOrderByCreatedAtAscIdAsc(disputeId)).thenReturn(List.of(
                DisputeHistory.builder()
                        .id(101L)
                        .disputeId(disputeId)
                        .eventType(DisputeHistoryEventType.MERCHANT_RESPONSE)
                        .actorUserId(merchantUserId)
                        .actorRole("MERCHANT")
                        .message("Merchant da phan hoi")
                        .resultingStatus("IN_REVIEW")
                        .createdAt(LocalDateTime.now().minusHours(1))
                        .build()
        ));

        DisputeDetailDto detail = disputeService.getMyDisputeDetail(driverUserId, disputeId);

        assertEquals(DriverDisputeStatus.IN_REVIEW, detail.getStatus());
        assertEquals(1, detail.getTimeline().size());
        assertEquals("MERCHANT", detail.getTimeline().get(0).getActorRole());

        when(disputeRepository.findByIdAndDriverId(disputeId, 999L)).thenReturn(Optional.empty());
        AppException notOwned = assertThrows(AppException.class,
                () -> disputeService.getMyDisputeDetail(999L, disputeId));
        assertEquals(ErrorCode.NOT_FOUND, notOwned.getErrorCode());
    }

    @Test
    @DisplayName("Add merchant response appends history entry and enforces ownership and status")
    void testAddMerchantResponse() {
        Dispute pending = Dispute.builder()
                .id(disputeId)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .status(DisputeStatusEnum.PENDING)
                .build();
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(pending));
        when(disputeHistoryRepository.save(any(DisputeHistory.class))).thenAnswer(inv -> {
            DisputeHistory h = inv.getArgument(0);
            h.setId(55L);
            h.setCreatedAt(LocalDateTime.now());
            return h;
        });

        DisputeTimelineEntryDto entry = disputeService.addMerchantResponse(
                merchantUserId, disputeId, new CreateDisputeResponseRequest("  Da xu ly voi tai xe  "));

        assertEquals(55L, entry.getId());
        assertEquals(DisputeHistoryEventType.MERCHANT_RESPONSE, entry.getEventType());
        assertEquals("MERCHANT", entry.getActorRole());
        assertEquals("Da xu ly voi tai xe", entry.getMessage());
        assertEquals(DriverDisputeStatus.IN_REVIEW, entry.getResultingStatus());

        AppException wrongMerchant = assertThrows(AppException.class, () ->
                disputeService.addMerchantResponse(999L, disputeId, new CreateDisputeResponseRequest("Nope")));
        assertEquals(ErrorCode.FORBIDDEN, wrongMerchant.getErrorCode());

        Dispute closed = Dispute.builder()
                .id(disputeId)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .status(DisputeStatusEnum.RESOLVED)
                .build();
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(closed));
        AppException conflict = assertThrows(AppException.class, () ->
                disputeService.addMerchantResponse(merchantUserId, disputeId, new CreateDisputeResponseRequest("Late")));
        assertEquals(ErrorCode.CONFLICT, conflict.getErrorCode());
    }

    @Test
    @DisplayName("Legacy getDisputeDetail enforces strict role-scoped ownership to prevent IDOR")
    void testGetDisputeDetailRoleScoped() {
        Dispute dispute = Dispute.builder()
                .id(disputeId)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .status(DisputeStatusEnum.PENDING)
                .build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        LegacyDisputeDto merchantView = disputeService.getDisputeDetail(merchantUserId, "ROLE_MERCHANT", disputeId);
        assertEquals(disputeId, merchantView.getId());
        assertEquals(DisputeStatusEnum.PENDING, merchantView.getStatus());

        AppException merchantUsingDriverRole = assertThrows(AppException.class,
                () -> disputeService.getDisputeDetail(merchantUserId, "ROLE_DRIVER", disputeId));
        assertEquals(ErrorCode.FORBIDDEN, merchantUsingDriverRole.getErrorCode());

        AppException driverUsingMerchantRole = assertThrows(AppException.class,
                () -> disputeService.getDisputeDetail(driverUserId, "ROLE_MERCHANT", disputeId));
        assertEquals(ErrorCode.FORBIDDEN, driverUsingMerchantRole.getErrorCode());
    }

    @Test
    @DisplayName("Get merchant disputes returns legacy DTO page")
    void testGetMerchantDisputes() {
        Dispute d = Dispute.builder().id(2L).merchantId(merchantUserId).status(DisputeStatusEnum.PENDING).build();
        when(disputeRepository.findByMerchantIdOrderByCreatedAtDesc(eq(merchantUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 10), 1));

        PageResponse<LegacyDisputeDto> page = disputeService.getMerchantDisputes(merchantUserId, PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
        assertEquals(DisputeStatusEnum.PENDING, page.getContent().get(0).getStatus());
    }

    @Test
    @DisplayName("Admin resolves dispute with row lock and records history entry atomically")
    void testResolveDisputeSuccess() {
        Dispute dispute = Dispute.builder()
                .id(disputeId)
                .status(DisputeStatusEnum.PENDING)
                .build();
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));
        when(disputeHistoryRepository.save(any(DisputeHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminDisputeResponse response = disputeService.resolveDispute(1L, disputeId, "Hoàn trả 50.000đ cho khách");

        assertNotNull(response);
        assertEquals(DisputeStatusEnum.RESOLVED, response.getStatus());
        assertEquals("Hoàn trả 50.000đ cho khách", response.getResolution());

        ArgumentCaptor<DisputeHistory> historyCaptor = ArgumentCaptor.forClass(DisputeHistory.class);
        verify(disputeHistoryRepository).save(historyCaptor.capture());
        assertEquals(DisputeHistoryEventType.ADMIN_RESOLUTION, historyCaptor.getValue().getEventType());
        assertEquals("ADMIN", historyCaptor.getValue().getActorRole());
        assertEquals("RESOLVED", historyCaptor.getValue().getResultingStatus());
    }

    @Test
    @DisplayName("Admin resolve dispute throws NOT_FOUND when dispute does not exist")
    void testResolveDisputeNotFound() {
        when(disputeRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

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
}
