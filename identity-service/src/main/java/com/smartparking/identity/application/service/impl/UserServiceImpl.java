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

import com.smartparking.common.event.UserWarnedEvent;
import com.smartparking.identity.domain.model.UserWarning;
import com.smartparking.identity.domain.repository.UserWarningRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;
    private final UserWarningRepository userWarningRepository;

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

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
        warnUser(adminUserId, targetUserId, UserWarningRequest.builder()
                .reason(reason != null && !reason.isBlank() ? reason : "Cảnh báo từ Quản trị viên")
                .penaltyType("OFFICIAL_WARNING")
                .notifyUser(true)
                .build());
    }

    @Override
    @Transactional
    public UserWarningResponse warnUser(Long adminUserId, Long targetUserId, UserWarningRequest request) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        if (targetUser.getRole() == RoleEnum.ADMIN) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể kỷ luật tài khoản Quản trị viên!");
        }

        User adminUser = userRepository.findById(adminUserId).orElse(null);
        String adminName = adminUser != null ? adminUser.getFullName() : "Admin #" + adminUserId;

        String reason = request != null && request.getReason() != null ? request.getReason() : "Cảnh báo từ Quản trị viên";
        String penaltyType = request != null && request.getPenaltyType() != null ? request.getPenaltyType() : "OFFICIAL_WARNING";
        Boolean notifyUser = request != null && request.getNotifyUser() != null ? request.getNotifyUser() : true;
        String evidenceUrl = request != null ? request.getEvidenceUrl() : null;

        // 1. Create and save UserWarning record
        UserWarning warning = UserWarning.builder()
                .user(targetUser)
                .reason(reason)
                .penaltyType(penaltyType)
                .notifyUser(notifyUser)
                .evidenceUrl(evidenceUrl)
                .issuedByAdminId(adminUserId)
                .build();
        UserWarning savedWarning = userWarningRepository.save(warning);

        // 2. Increment warning count
        int currentCount = targetUser.getWarningCount() != null ? targetUser.getWarningCount() : 0;
        int newCount = currentCount + 1;
        targetUser.setWarningCount(newCount);

        // 3. Auto-suspension rule (threshold: >= 3)
        if (newCount >= 3) {
            targetUser.setStatus(UserStatusEnum.SUSPENDED);
            targetUser.setSuspendedUntil(LocalDateTime.now().plusDays(7));
            targetUser.setStatusReason("Tự động tạm khóa 7 ngày do đạt " + newCount + " lần cảnh cáo vi phạm: " + reason);
        }
        userRepository.save(targetUser);

        // 4. Audit log
        auditLogRepository.save(AuditLog.builder()
                .actorUserId(adminUserId)
                .action("WARN_USER")
                .targetType("User")
                .targetId(targetUser.getId())
                .detail(String.format("Lần %d - Lý do: %s (Trạng thái: %s)", newCount, reason, targetUser.getStatus()))
                .result("SUCCESS")
                .build());

        // 5. Publish Kafka Event if notifyUser is true
        if (Boolean.TRUE.equals(notifyUser)) {
            publishUserWarnedEvent(savedWarning, targetUser, adminUserId);
        }

        return UserWarningResponse.builder()
                .id(savedWarning.getId())
                .userId(targetUser.getId())
                .userName(targetUser.getFullName())
                .userEmail(targetUser.getEmail())
                .userPhone(targetUser.getPhone())
                .reason(savedWarning.getReason())
                .penaltyType(savedWarning.getPenaltyType())
                .evidenceUrl(savedWarning.getEvidenceUrl())
                .warningCount(newCount)
                .userStatus(targetUser.getStatus())
                .suspendedUntil(targetUser.getSuspendedUntil())
                .issuedByAdminId(adminUserId)
                .adminName(adminName)
                .createdAt(savedWarning.getCreatedAt() != null ? savedWarning.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserWarningItemResponse> getUserWarnings(Long targetUserId) {
        if (!userRepository.existsById(targetUserId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng");
        }

        List<UserWarning> warnings = userWarningRepository.findByUserIdOrderByCreatedAtDesc(targetUserId);
        Map<Long, String> adminNames = new HashMap<>();

        return warnings.stream().map(w -> {
            String adminName = adminNames.computeIfAbsent(w.getIssuedByAdminId(), id ->
                    userRepository.findById(id).map(User::getFullName).orElse("Admin #" + id)
            );
            return UserWarningItemResponse.builder()
                    .id(w.getId())
                    .reason(w.getReason())
                    .penaltyType(w.getPenaltyType())
                    .evidenceUrl(w.getEvidenceUrl())
                    .issuedByAdminId(w.getIssuedByAdminId())
                    .adminName(adminName)
                    .createdAt(w.getCreatedAt() != null ? w.getCreatedAt() : LocalDateTime.now())
                    .build();
        }).collect(Collectors.toList());
    }

    private void publishUserWarnedEvent(UserWarning warning, User user, Long adminUserId) {
        if (kafkaTemplate == null) {
            log.info("KafkaTemplate is not available, skipping USER_WARNED event publish for user {}", user.getId());
            return;
        }
        try {
            UserWarnedEvent event = UserWarnedEvent.builder()
                    .eventType("USER_WARNED")
                    .aggregateId(String.valueOf(user.getId()))
                    .warningId(warning.getId())
                    .userId(user.getId())
                    .userEmail(user.getEmail())
                    .userPhone(user.getPhone())
                    .userFullName(user.getFullName())
                    .reason(warning.getReason())
                    .penaltyType(warning.getPenaltyType())
                    .evidenceUrl(warning.getEvidenceUrl())
                    .warningCount(user.getWarningCount())
                    .isSuspended(user.getStatus() == UserStatusEnum.SUSPENDED)
                    .suspendedUntil(user.getSuspendedUntil() != null
                            ? user.getSuspendedUntil().atZone(ZoneId.systemDefault()).toInstant()
                            : null)
                    .issuedByAdminId(adminUserId)
                    .build();
            kafkaTemplate.send("user.events", String.valueOf(user.getId()), event);
            log.info("Published USER_WARNED event to topic 'user.events' for user {}", user.getId());
        } catch (Exception ex) {
            log.error("Failed to publish USER_WARNED event for user {}: {}", user.getId(), ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(Pageable pageable) {
        Pageable safe = Pageables.sanitize(pageable);
        Page<AuditLog> page = auditLogRepository.findAll(safe);
        return new PageResponse<>(page.map(AuditLogResponse::from));
    }
}
