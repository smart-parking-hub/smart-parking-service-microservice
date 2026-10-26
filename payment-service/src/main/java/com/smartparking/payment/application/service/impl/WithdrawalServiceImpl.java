package com.smartparking.payment.application.service.impl;

import com.smartparking.common.dto.PageResponse;
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
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy yêu cầu rút tiền"));

        if (withdrawal.getStatus() != WithdrawalStatusEnum.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu rút tiền này đã được xử lý");
        }

        withdrawal.setProcessedBy(adminUserId);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawal.setDecisionReason(reason);

        if (approved) {
            withdrawal.setStatus(WithdrawalStatusEnum.APPROVED);
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
        BigDecimal currentBalance = merchantWalletService.getWalletBalance(withdrawal.getMerchantId());
        return WithdrawalResponse.fromEntity(saved, currentBalance);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> getAllWithdrawalsForAdmin(Pageable pageable) {
        Page<WithdrawalResponse> page = withdrawalRequestRepository.findAll(pageable)
                .map(req -> {
                    BigDecimal balance = merchantWalletService.getWalletBalance(req.getMerchantId());
                    return WithdrawalResponse.fromEntity(req, balance);
                });
        return new PageResponse<>(page);
    }
}
