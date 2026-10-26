package com.smartparking.payment.application.service.impl;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.service.DisputeService;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.domain.model.Dispute;
import com.smartparking.payment.domain.repository.DisputeRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final MerchantWalletService merchantWalletService;

    @Override
    @Transactional
    public DisputeDto createDispute(Long driverUserId, CreateDisputeRequest request) {
        disputeRepository.findByBookingId(request.getBookingId()).ifPresent(d -> {
            throw new AppException(ErrorCode.CONFLICT, "Booking này đã có khiếu nại đang được xử lý");
        });

        Dispute dispute = Dispute.builder()
                .bookingId(request.getBookingId())
                .driverId(driverUserId)
                .merchantId(request.getMerchantId())
                .reason(request.getReason())
                .description(request.getDescription())
                .status(DisputeStatusEnum.PENDING)
                .build();

        Dispute saved = disputeRepository.save(dispute);
        log.info("Created dispute {} for booking {}", saved.getId(), request.getBookingId());
        return DisputeDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DisputeDto> getMyDisputes(Long driverUserId, Pageable pageable) {
        Page<DisputeDto> page = disputeRepository.findByDriverIdOrderByCreatedAtDesc(driverUserId, pageable)
                .map(DisputeDto::fromEntity);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DisputeDto> getMerchantDisputes(Long merchantUserId, Pageable pageable) {
        Page<DisputeDto> page = disputeRepository.findByMerchantIdOrderByCreatedAtDesc(merchantUserId, pageable)
                .map(DisputeDto::fromEntity);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeDto getDisputeDetail(Long userId, Long id) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khiếu nại"));
        if (!dispute.getDriverId().equals(userId) && (dispute.getMerchantId() == null || !dispute.getMerchantId().equals(userId))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập khiếu nại này");
        }
        return DisputeDto.fromEntity(dispute);
    }

    @Override
    @Transactional
    public AdminDisputeResponse resolveDispute(Long adminUserId, Long id, String resolution) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khiếu nại"));

        dispute.setStatus(DisputeStatusEnum.RESOLVED);
        dispute.setResolution(resolution);
        Dispute saved = disputeRepository.save(dispute);

        // Execute financial adjustment if resolution requires refund
        if (resolution != null && resolution.toUpperCase().contains("REFUND")) {
            paymentRepository.findByBookingId(dispute.getBookingId()).stream()
                    .filter(p -> p.getStatus() == com.smartparking.common.enums.PaymentStatusEnum.SUCCESS)
                    .forEach(payment -> {
                        payment.setStatus(com.smartparking.common.enums.PaymentStatusEnum.REFUNDED);
                        paymentRepository.save(payment);
                        log.info("Payment {} marked REFUNDED due to dispute resolution #{}", payment.getId(), id);

                        if (dispute.getMerchantId() != null) {
                            try {
                                merchantWalletService.holdWithdrawal(
                                        dispute.getMerchantId(),
                                        payment.getAmount(),
                                        "Thu hồi doanh thu do giải quyết khiếu nại #" + id + ": " + resolution);
                            } catch (Exception ex) {
                                log.warn("Could not debit merchant wallet on dispute refund: {}", ex.getMessage());
                            }
                        }
                    });
        }

        log.info("Admin {} resolved dispute {}", adminUserId, id);
        return AdminDisputeResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminDisputeResponse> getAdminDisputes(Pageable pageable) {
        Page<AdminDisputeResponse> page = disputeRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AdminDisputeResponse::fromEntity);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDisputeResponse getAdminDispute(Long id) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khiếu nại"));
        return AdminDisputeResponse.fromEntity(dispute);
    }
}
