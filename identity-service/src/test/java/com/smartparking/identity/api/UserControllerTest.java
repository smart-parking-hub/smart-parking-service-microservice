package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.identity.application.dto.ChangePasswordRequest;
import com.smartparking.identity.application.dto.UserProfileDto;
import com.smartparking.identity.application.service.UserService;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    private CustomUserDetails user;

    @BeforeEach
    void setUp() {
        user = new CustomUserDetails(100L, "0912345678", "pass", UserStatusEnum.ACTIVE, Collections.emptyList());
    }

    @Test
    @DisplayName("getProfile returns user profile")
    void testGetProfile() {
        UserProfileDto dto = UserProfileDto.builder().fullName("Test Name").phone("0912345678").build();
        when(userService.getProfile(100L)).thenReturn(dto);

        ResponseEntity<ApiResponse<UserProfileDto>> res = controller.getProfile(user);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("Test Name", res.getBody().getData().getFullName());
    }

    @Test
    @DisplayName("updateProfile returns updated profile")
    void testUpdateProfile() {
        UserProfileDto req = UserProfileDto.builder().fullName("New Name").build();
        UserProfileDto dto = UserProfileDto.builder().fullName("New Name").build();
        when(userService.updateProfile(100L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<UserProfileDto>> res = controller.updateProfile(user, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("New Name", res.getBody().getData().getFullName());
    }

    @Test
    @DisplayName("changePassword delegates to userService")
    void testChangePassword() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        ResponseEntity<ApiResponse<Void>> res = controller.changePassword(user, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService, times(1)).changePassword(100L, req);
    }
}
