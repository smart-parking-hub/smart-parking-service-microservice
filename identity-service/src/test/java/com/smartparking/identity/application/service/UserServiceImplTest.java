package com.smartparking.identity.application.service;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.ChangePasswordRequest;
import com.smartparking.identity.application.dto.UserProfileDto;
import com.smartparking.identity.application.service.impl.UserServiceImpl;
import com.smartparking.identity.domain.model.Driver;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.AuditLogRepository;
import com.smartparking.identity.domain.repository.DriverRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private final Long userId = 100L;

    @Test
    @DisplayName("Get profile successfully")
    void testGetProfileSuccess() {
        User user = User.builder()
                .id(userId)
                .fullName("Nguyen Van Test")
                .phone("0911222333")
                .email("test@example.com")
                .emergencyContact("0999888777")
                .address("123 Duong 1, Q1, HCM")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileDto dto = userService.getProfile(userId);

        assertNotNull(dto);
        assertEquals("Nguyen Van Test", dto.getFullName());
        assertEquals("0911222333", dto.getPhone());
        assertEquals("test@example.com", dto.getEmail());
    }

    @Test
    @DisplayName("Get profile throws USER_NOT_FOUND when user does not exist")
    void testGetProfileNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> userService.getProfile(userId));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Update profile updates user and driver record")
    void testUpdateProfileSuccess() {
        User user = User.builder()
                .id(userId)
                .fullName("Old Name")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Driver driver = Driver.builder()
                .fullName("Old Name")
                .build();
        when(driverRepository.findByUserId(userId)).thenReturn(Optional.of(driver));

        UserProfileDto updateReq = UserProfileDto.builder()
                .fullName("New Name")
                .email("new@example.com")
                .address("456 Duong 2")
                .build();

        UserProfileDto result = userService.updateProfile(userId, updateReq);

        assertNotNull(result);
        assertEquals("New Name", user.getFullName());
        assertEquals("New Name", driver.getFullName());
        verify(userRepository, times(1)).save(user);
        verify(driverRepository, times(1)).save(driver);
    }

    @Test
    @DisplayName("Change password successfully with matching current password")
    void testChangePasswordSuccess() {
        User user = User.builder()
                .id(userId)
                .passwordHash("oldHashedPassword")
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPass", "oldHashedPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPass123")).thenReturn("newHashedPassword");

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("CurrentPass");
        req.setNewPassword("NewPass123");

        userService.changePassword(userId, req);

        assertEquals("newHashedPassword", user.getPasswordHash());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("Change password fails with wrong current password")
    void testChangePasswordWrongCurrent() {
        User user = User.builder()
                .id(userId)
                .passwordHash("oldHashedPassword")
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass", "oldHashedPassword")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("WrongPass");
        req.setNewPassword("NewPass123");

        AppException ex = assertThrows(AppException.class, () -> userService.changePassword(userId, req));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAllUsersForAdmin returns page of admin users")
    void testGetAllUsersForAdmin() {
        User user = User.builder()
                .id(1L)
                .phone("0987654321")
                .email("admin@test.com")
                .role(com.smartparking.common.enums.RoleEnum.ADMIN)
                .status(com.smartparking.common.enums.UserStatusEnum.ACTIVE)
                .build();
        org.springframework.data.domain.Page<User> page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(user));
        when(userRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        com.smartparking.common.dto.PageResponse<com.smartparking.identity.application.dto.AdminUserResponse> res =
                userService.getAllUsersForAdmin(org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(res);
        assertEquals(1, res.getContent().size());
        assertEquals("0987654321", res.getContent().get(0).getPhone());
    }

    @Test
    @DisplayName("getUserDetailForAdmin returns detail when found")
    void testGetUserDetailForAdmin() {
        User user = User.builder()
                .id(2L)
                .phone("0912345678")
                .fullName("Nguyen Van A")
                .role(com.smartparking.common.enums.RoleEnum.DRIVER)
                .status(com.smartparking.common.enums.UserStatusEnum.ACTIVE)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        com.smartparking.identity.application.dto.AdminUserDetailResponse res = userService.getUserDetailForAdmin(2L);
        assertNotNull(res);
        assertEquals("Nguyen Van A", res.getFullName());
    }

    @Test
    @DisplayName("banUser successfully bans an active user")
    void testBanUserSuccess() {
        User user = User.builder()
                .id(2L)
                .role(com.smartparking.common.enums.RoleEnum.DRIVER)
                .status(com.smartparking.common.enums.UserStatusEnum.ACTIVE)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.banUser(1L, 2L, "Spam");

        assertEquals(com.smartparking.common.enums.UserStatusEnum.BANNED, user.getStatus());
        assertEquals("Spam", user.getStatusReason());
        verify(userRepository).save(user);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("banUser throws when admin bans self")
    void testBanUserSelf() {
        AppException ex = assertThrows(AppException.class, () -> userService.banUser(1L, 1L, "Self"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("banUser throws when target is another admin")
    void testBanUserOtherAdmin() {
        User targetAdmin = User.builder()
                .id(2L)
                .role(com.smartparking.common.enums.RoleEnum.ADMIN)
                .status(com.smartparking.common.enums.UserStatusEnum.ACTIVE)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        AppException ex = assertThrows(AppException.class, () -> userService.banUser(1L, 2L, "Admin ban"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("banUser throws when target already banned")
    void testBanUserAlreadyBanned() {
        User banned = User.builder()
                .id(2L)
                .role(com.smartparking.common.enums.RoleEnum.DRIVER)
                .status(com.smartparking.common.enums.UserStatusEnum.BANNED)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(banned));

        AppException ex = assertThrows(AppException.class, () -> userService.banUser(1L, 2L, "Already"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("unbanUser successfully unbans a banned user")
    void testUnbanUserSuccess() {
        User user = User.builder()
                .id(2L)
                .role(com.smartparking.common.enums.RoleEnum.DRIVER)
                .status(com.smartparking.common.enums.UserStatusEnum.BANNED)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.unbanUser(1L, 2L, "Verified");

        assertEquals(com.smartparking.common.enums.UserStatusEnum.ACTIVE, user.getStatus());
        assertEquals("Verified", user.getStatusReason());
        verify(userRepository).save(user);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("unbanUser throws when user is already active")
    void testUnbanUserAlreadyActive() {
        User user = User.builder()
                .id(2L)
                .role(com.smartparking.common.enums.RoleEnum.DRIVER)
                .status(com.smartparking.common.enums.UserStatusEnum.ACTIVE)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class, () -> userService.unbanUser(1L, 2L, "Active"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("warnUser writes audit log for warning")
    void testWarnUserSuccess() {
        User user = User.builder().id(2L).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.warnUser(1L, 2L, "Warning note");
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("getAuditLogs returns page of audit logs")
    void testGetAuditLogs() {
        com.smartparking.identity.domain.model.AuditLog log = com.smartparking.identity.domain.model.AuditLog.builder()
                .id(1L)
                .actorUserId(1L)
                .action("BAN_USER")
                .targetType("User")
                .targetId(2L)
                .build();
        org.springframework.data.domain.Page<com.smartparking.identity.domain.model.AuditLog> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(log));
        when(auditLogRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        com.smartparking.common.dto.PageResponse<com.smartparking.identity.application.dto.AuditLogResponse> res =
                userService.getAuditLogs(org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(res);
        assertEquals(1, res.getContent().size());
        assertEquals("BAN_USER", res.getContent().get(0).getAction());
    }
}
