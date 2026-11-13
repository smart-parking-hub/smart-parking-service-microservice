package com.smartparking.payment.application.service.impl;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.WithdrawalDecisionEvent;
import com.smartparking.common.messaging.TransactionalEventOutbox;
import com.smartparking.common.enums.WithdrawalStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.CreateWithdrawalRequestDto;
import com.smartparking.payment.application.dto.WithdrawalResponse;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.application.service.WithdrawalService;
import com.smartparking.payment.domain.model.WithdrawalRequest;
import com.smartparking.payment.domain.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalServiceImpl implements WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final MerchantWalletService merchantWalletService;
    private final TransactionalEventOutbox adminEventOutbox;

    @Override
    @Transactional
    public WithdrawalResponse requestWithdrawal(Long merchantUserId, CreateWithdrawalRequestDto request) {
        BigDecimal balance = merchantWalletService.getWalletBalance(merchantUserId);
        if (balance.compareTo(request.getAmount()) < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số dư khả dụng không đủ để rút tiền!");
        }

        merchantWalletService.holdWithdrawal(
                merchantUserId,
                request.getAmount(),
                "Yêu cầu rút tiền #" + System.currentTimeMillis());

        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .merchantId(merchantUserId)
                .amount(request.getAmount())
                .status(WithdrawalStatusEnum.PENDING)
                .bankName(request.getBankName())
                .bankAccountNumber(request.getBankAccountNumber())
                .accountHolderName(request.getAccountHolderName())
                .requestedAt(LocalDateTime.now())
                .build();

        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);
        adminEventOutbox.enqueue(AdminOperationEvent.TOPIC, "withdrawal:" + saved.getId(),
                AdminOperationEvent.builder().eventType(AdminOperationEvent.WITHDRAWAL_REQUESTED)
                        .aggregateId(String.valueOf(saved.getId())).targetId(saved.getId()).actorId(merchantUserId)
                        .amount(saved.getAmount()).currency("VND").build());
        BigDecimal remainingBalance = merchantWalletService.getWalletBalance(merchantUserId);

        log.info("Merchant {} submitted withdrawal request {} for amount {}",
                merchantUserId, saved.getId(), request.getAmount());

        return WithdrawalResponse.fromEntity(saved, remainingBalance);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> getMyWithdrawals(Long merchantUserId, Pageable pageable) {
        BigDecimal balance = merchantWalletService.getWalletBalance(merchantUserId);
        Page<WithdrawalResponse> page = withdrawalRequestRepository
                .findByMerchantIdOrderByRequestedAtDesc(merchantUserId, pageable)
                .map(req -> WithdrawalResponse.fromEntity(req, balance));

        return new PageResponse<>(page);
    }

    @Override
    @Transactional
    public WithdrawalResponse processWithdrawal(Long adminUserId, Long withdrawalId, boolean approved, String reason) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findForUpdate(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy yêu cầu rút tiền"));

        if (withdrawal.getStatus() != WithdrawalStatusEnum.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu rút tiền này đã được xử lý");
        }

        withdrawal.setProcessedBy(adminUserId);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawal.setDecisionReason(reason);

        if (approved) {
            withdrawal.setStatus(WithdrawalStatusEnum.APPROVED);
            merchantWalletService.settleApprovedWithdrawal(
                    withdrawal.getMerchantId(),
                    withdrawalId,
                    withdrawal.getAmount(),
                    "Phê duyệt rút tiền #" + withdrawalId + ": " + reason);
            log.info("Admin {} approved withdrawal {}", adminUserId, withdrawalId);
        } else {
            withdrawal.setStatus(WithdrawalStatusEnum.REJECTED);
            merchantWalletService.refundWithdrawal(
                    withdrawal.getMerchantId(),
                    withdrawal.getAmount(),
                    "Hoàn tiền yêu cầu rút #" + withdrawalId + " bị từ chối: " + reason);
            log.info("Admin {} rejected withdrawal {}. Refunded to merchant wallet.", adminUserId, withdrawalId);
        }

        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);
        adminEventOutbox.enqueue(WithdrawalDecisionEvent.TOPIC, "withdrawal:" + saved.getId(),
                WithdrawalDecisionEvent.builder()
                        .eventType(approved ? WithdrawalDecisionEvent.APPROVED : WithdrawalDecisionEvent.REJECTED)
                        .aggregateId(String.valueOf(saved.getId())).withdrawalId(saved.getId())
                        .merchantId(saved.getMerchantId()).amount(saved.getAmount()).currency("VND").build());
        BigDecimal currentBalance = merchantWalletService.getWalletBalance(withdrawal.getMerchantId());
        return WithdrawalResponse.fromEntity(saved, currentBalance);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> getAllWithdrawalsForAdmin(Pageable pageable) {
        var safe = com.smartparking.common.util.Pageables.sanitize(pageable);
        var requested = safe.getSort().stream().findFirst().orElse(
                new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "requestedAt"));
        String property = java.util.Set.of("requestedAt", "amount", "id").contains(requested.getProperty())
                ? requested.getProperty() : "requestedAt";
        var sort = org.springframework.data.domain.Sort.by(requested.getDirection(), property);
        if (!property.equals("id")) sort = sort.and(org.springframework.data.domain.Sort.by(requested.getDirection(), "id"));
        var sorted = org.springframework.data.domain.PageRequest.of(safe.getPageNumber(), safe.getPageSize(), sort);
        Page<WithdrawalResponse> page = withdrawalRequestRepository.findAll(sorted)
                .map(req -> {
                    BigDecimal balance = merchantWalletService.getWalletBalance(req.getMerchantId());
                    return WithdrawalResponse.fromEntity(req, balance);
                });
        return new PageResponse<>(page);
    }
}
