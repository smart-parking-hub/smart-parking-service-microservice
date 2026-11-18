package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.identity.application.dto.MerchantProfileResponse;
import com.smartparking.identity.application.dto.UpdateMerchantProfileRequest;
import com.smartparking.identity.application.service.MerchantProfileService;
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
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantProfileControllerTest {

    @Mock
    private MerchantProfileService merchantProfileService;

    @InjectMocks
    private MerchantProfileController controller;

    private CustomUserDetails user;

    @BeforeEach
    void setUp() {
        user = new CustomUserDetails(200L, "0988776655", "pass", UserStatusEnum.ACTIVE, Collections.emptyList());
    }

    @Test
    @DisplayName("getProfile returns MerchantProfileResponse")
    void testGetProfile() {
        MerchantProfileResponse profile = MerchantProfileResponse.builder().userId(200L).build();
        when(merchantProfileService.getProfile(200L)).thenReturn(profile);

        ResponseEntity<ApiResponse<MerchantProfileResponse>> res = controller.getProfile(user);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(200L, res.getBody().getData().getUserId());
    }

    @Test
    @DisplayName("updateProfile returns updated profile")
    void testUpdateProfile() {
        UpdateMerchantProfileRequest req = new UpdateMerchantProfileRequest();
        MerchantProfileResponse profile = MerchantProfileResponse.builder().userId(200L).build();
        when(merchantProfileService.updateProfile(200L, req)).thenReturn(profile);

        ResponseEntity<ApiResponse<MerchantProfileResponse>> res = controller.updateProfile(user, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(200L, res.getBody().getData().getUserId());
    }

    @Test
    @DisplayName("uploadDocuments returns updated profile with document")
    void testUploadDocuments() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());
        MerchantProfileResponse profile = MerchantProfileResponse.builder().userId(200L).build();
        when(merchantProfileService.uploadDocument(200L, "GPKD-123", file)).thenReturn(profile);

        ResponseEntity<ApiResponse<MerchantProfileResponse>> res = controller.uploadDocuments(user, "GPKD-123", file);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("Đã lưu giấy tờ vào bản nháp hồ sơ xác minh", res.getBody().getMessage());
        assertEquals(200L, res.getBody().getData().getUserId());
    }
}
