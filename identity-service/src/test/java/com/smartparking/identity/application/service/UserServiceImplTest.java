package com.smartparking.identity.application.service;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.ChangePasswordRequest;
import com.smartparking.identity.application.dto.UserProfileDto;
import com.smartparking.identity.application.service.impl.UserServiceImpl;
import com.smartparking.identity.domain.model.Driver;
import com.smartparking.identity.domain.model.User;
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
}
