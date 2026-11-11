package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.CreateDisputeResponseRequest;
import com.smartparking.payment.application.dto.DisputeDetailDto;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.dto.DisputeTimelineEntryDto;
import com.smartparking.payment.application.dto.LegacyDisputeDto;
import org.springframework.data.domain.Pageable;

public interface DisputeService {
    DisputeDto createDispute(Long driverUserId, CreateDisputeRequest request);
    PageResponse<DisputeDto> getMyDisputes(Long driverUserId, Integer page, Integer size, String status);
    DisputeDetailDto getMyDisputeDetail(Long driverUserId, Long id);
    DisputeTimelineEntryDto addMerchantResponse(Long merchantUserId, Long id, CreateDisputeResponseRequest request);
    PageResponse<LegacyDisputeDto> getMerchantDisputes(Long merchantUserId, Pageable pageable);
    LegacyDisputeDto getDisputeDetail(Long userId, String role, Long id);
    AdminDisputeResponse resolveDispute(Long adminUserId, Long id, String resolution);
    PageResponse<AdminDisputeResponse> getAdminDisputes(Pageable pageable);
    AdminDisputeResponse getAdminDispute(Long id);
}
