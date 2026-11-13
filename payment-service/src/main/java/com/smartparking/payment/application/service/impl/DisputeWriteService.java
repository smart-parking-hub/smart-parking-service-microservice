package com.smartparking.payment.application.service.impl;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.messaging.TransactionalEventOutbox;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.domain.model.Dispute;
import com.smartparking.payment.domain.model.DisputeEvidenceFile;
import com.smartparking.payment.domain.repository.DisputeEvidenceFileRepository;
import com.smartparking.payment.domain.repository.DisputeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeWriteService {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceFileRepository disputeEvidenceFileRepository;
    private final TransactionalEventOutbox adminEventOutbox;

    @Transactional
    public Dispute createDisputeRecord(
            Long driverUserId,
            Long bookingId,
            Long merchantId,
            Long parkingLotId,
            String reason,
            String description,
            List<String> evidenceUrls) {
        if (disputeRepository.findByBookingId(bookingId).isPresent()) {
            throw new AppException(ErrorCode.CONFLICT, "Booking này đã có khiếu nại");
        }

        List<DisputeEvidenceFile> filesToAttach = lockAndValidateEvidenceFiles(driverUserId, evidenceUrls);

        Dispute dispute = Dispute.builder()
                .bookingId(bookingId)
                .driverId(driverUserId)
                .merchantId(merchantId)
                .parkingLotId(parkingLotId)
                .reason(reason)
                .description(description)
                .evidenceUrls(evidenceUrls == null ? Collections.emptyList() : List.copyOf(evidenceUrls))
                .status(DisputeStatusEnum.PENDING)
                .build();

        try {
            Dispute saved = disputeRepository.saveAndFlush(dispute);
            if (!filesToAttach.isEmpty()) {
                for (DisputeEvidenceFile file : filesToAttach) {
                    file.setDisputeId(saved.getId());
                }
                disputeEvidenceFileRepository.saveAllAndFlush(filesToAttach);
            }
            adminEventOutbox.enqueue(AdminOperationEvent.TOPIC, "dispute:" + saved.getId(),
                    AdminOperationEvent.builder().eventType(AdminOperationEvent.DISPUTE_OPENED)
                            .aggregateId(String.valueOf(saved.getId())).targetId(saved.getId()).actorId(driverUserId).build());
            log.info("Created dispute {} for booking {}", saved.getId(), bookingId);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            if (isBookingUniqueViolation(ex)) {
                throw new AppException(ErrorCode.CONFLICT, "Booking này đã có khiếu nại");
            }
            throw ex;
        }
    }

    private List<DisputeEvidenceFile> lockAndValidateEvidenceFiles(Long driverUserId, List<String> evidenceUrls) {
        if (evidenceUrls == null || evidenceUrls.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> internalFileNames = evidenceUrls.stream()
                .map(DisputeEvidenceServiceImpl::extractInternalFileName)
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .toList();
        if (internalFileNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<DisputeEvidenceFile> lockedFiles = disputeEvidenceFileRepository
                .findByStoredFileNameInForUpdate(internalFileNames);
        Map<String, DisputeEvidenceFile> byFileName = lockedFiles.stream()
                .collect(Collectors.toMap(DisputeEvidenceFile::getStoredFileName, Function.identity(), (a, b) -> a));

        for (String fileName : internalFileNames) {
            DisputeEvidenceFile file = byFileName.get(fileName);
            if (file == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tệp ảnh chứng từ không tồn tại hoặc chưa được tải lên");
            }
            if (!driverUserId.equals(file.getUploaderDriverId())) {
                throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền sử dụng tệp ảnh chứng từ này");
            }
            if (file.getDisputeId() != null) {
                throw new AppException(ErrorCode.CONFLICT, "Tệp ảnh chứng từ đã được gắn với một khiếu nại khác");
            }
        }
        return lockedFiles;
    }

    private boolean isBookingUniqueViolation(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConstraintViolationException cve) {
                String constraintName = cve.getConstraintName();
                if (constraintName != null && constraintName.toLowerCase(Locale.ROOT).contains("uk_disputes_booking_id")) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("uk_disputes_booking_id")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

