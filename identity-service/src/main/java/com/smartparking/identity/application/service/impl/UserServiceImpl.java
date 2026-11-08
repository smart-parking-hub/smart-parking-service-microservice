package com.smartparking.identity.application.service.impl;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.ChangePasswordRequest;
import com.smartparking.identity.application.dto.UserProfileDto;
import com.smartparking.identity.application.service.UserService;
import com.smartparking.identity.domain.model.Driver;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.DriverRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.common.util.Pageables;
import com.smartparking.identity.application.dto.*;
import com.smartparking.identity.domain.model.AuditLog;
import com.smartparking.identity.domain.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.smartparking.common.enums.RoleEnum;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        return UserProfileDto.builder()
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .emergencyContact(user.getEmergencyContact())
                .address(user.getAddress())
                .build();
    }

    @Override
    @Transactional
    public UserProfileDto updateProfile(Long userId, UserProfileDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setEmergencyContact(request.getEmergencyContact());
        user.setAddress(request.getAddress());
        userRepository.save(user);

        Driver driver = driverRepository.findByUserId(userId).orElse(null);
        if (driver != null && request.getFullName() != null) {
            driver.setFullName(request.getFullName());
            driverRepository.save(driver);
        }

        return request;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAllUsersForAdmin(Pageable pageable) {
        Pageable safe = Pageables.sanitize(pageable);
        Page<User> page = userRepository.findAll(safe);
        return new PageResponse<>(page.map(AdminUserResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetailForAdmin(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        return AdminUserDetailResponse.fromEntity(user, 0, 0);
    }

    @Override
    @Transactional
    public void banUser(Long adminUserId, Long targetUserId, String reason) {
        if (adminUserId.equals(targetUserId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Admin không thể tự khóa tài khoản");
        }
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getRole() == RoleEnum.ADMIN) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể khóa tài khoản quản trị viên khác");
        }
        if (user.getStatus() == UserStatusEnum.BANNED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản đã bị khóa!");
        }

        user.setStatus(UserStatusEnum.BANNED);
        user.setStatusReason(reason);
        userRepository.save(user);

        auditLogRepository.save(AuditLog.builder()
                .actorUserId(adminUserId)
                .action("BAN_USER")
                .targetType("User")
                .targetId(user.getId())
                .detail(reason)
                .result("SUCCESS")
                .build());
    }

    @Override
    @Transactional
    public void unbanUser(Long adminUserId, Long targetUserId, String reason) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getStatus() == UserStatusEnum.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản đang hoạt động!");
        }

        user.setStatus(UserStatusEnum.ACTIVE);
        user.setStatusReason(reason);
        userRepository.save(user);

        auditLogRepository.save(AuditLog.builder()
                .actorUserId(adminUserId)
                .action("UNBAN_USER")
                .targetType("User")
                .targetId(user.getId())
                .detail(reason)
                .result("SUCCESS")
                .build());
    }

    @Override
    @Transactional
    public void warnUser(Long adminUserId, Long targetUserId, String reason) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        auditLogRepository.save(AuditLog.builder()
                .actorUserId(adminUserId)
                .action("WARN_USER")
                .targetType("User")
                .targetId(user.getId())
                .detail(reason)
                .result("SUCCESS")
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(Pageable pageable) {
        Pageable safe = Pageables.sanitize(pageable);
        Page<AuditLog> page = auditLogRepository.findAll(safe);
        return new PageResponse<>(page.map(AuditLogResponse::from));
    }
}
