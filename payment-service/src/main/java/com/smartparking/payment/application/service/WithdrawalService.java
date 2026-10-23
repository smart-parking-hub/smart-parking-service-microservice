package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.CreateWithdrawalRequestDto;
import com.smartparking.payment.application.dto.WithdrawalResponse;
import org.springframework.data.domain.Pageable;

public interface WithdrawalService {
    WithdrawalResponse requestWithdrawal(Long merchantUserId, CreateWithdrawalRequestDto request);
    PageResponse<WithdrawalResponse> getMyWithdrawals(Long merchantUserId, Pageable pageable);
    WithdrawalResponse processWithdrawal(Long adminUserId, Long withdrawalId, boolean approved, String reason);
}
