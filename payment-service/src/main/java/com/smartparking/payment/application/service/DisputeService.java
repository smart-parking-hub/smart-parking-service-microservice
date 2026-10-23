package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.DisputeDto;
import org.springframework.data.domain.Pageable;

public interface DisputeService {
    DisputeDto createDispute(Long driverUserId, CreateDisputeRequest request);
    PageResponse<DisputeDto> getMyDisputes(Long driverUserId, Pageable pageable);
    PageResponse<DisputeDto> getMerchantDisputes(Long merchantUserId, Pageable pageable);
    DisputeDto getDisputeDetail(Long userId, Long id);
    AdminDisputeResponse resolveDispute(Long adminUserId, Long id, String resolution);
    PageResponse<AdminDisputeResponse> getAdminDisputes(Pageable pageable);
    AdminDisputeResponse getAdminDispute(Long id);
}
