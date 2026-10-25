package com.smartparking.identity.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.identity.application.dto.*;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserProfileDto getProfile(Long userId);
    UserProfileDto updateProfile(Long userId, UserProfileDto request);
    void changePassword(Long userId, ChangePasswordRequest request);

    PageResponse<AdminUserResponse> getAllUsersForAdmin(Pageable pageable);
    AdminUserDetailResponse getUserDetailForAdmin(Long id);
    void banUser(Long adminUserId, Long targetUserId, String reason);
    void unbanUser(Long adminUserId, Long targetUserId, String reason);
    void warnUser(Long adminUserId, Long targetUserId, String reason);
    PageResponse<AuditLogResponse> getAuditLogs(Pageable pageable);
}
