package com.smartparking.identity.application.service;

import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.MerchantProfileResponse;
import com.smartparking.identity.application.dto.UpdateMerchantProfileRequest;
import com.smartparking.identity.application.service.impl.MerchantProfileServiceImpl;
import com.smartparking.identity.domain.model.Merchant;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.MerchantRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantProfileServiceImplTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MerchantProfileServiceImpl merchantProfileService;

    private final Long merchantUserId = 200L;

    @Test
    @DisplayName("Get profile successfully with masked citizenId and bank account")
    void testGetProfileSuccess() {
        User user = User.builder()
                .id(merchantUserId)
                .fullName("Bui Thi Chu Bai")
                .phone("0988776655")
                .email("chubai@example.com")
                .status(UserStatusEnum.ACTIVE)
                .build();

        Merchant merchant = Merchant.builder()
                .id(15L)
                .user(user)
                .citizenId("079200009999")
                .bankName("Techcombank")
                .bankAccountNum("190333444555")
                .walletBalance(new BigDecimal("1500000"))
                .businessLicense("GPKD-12345")
                .build();

        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(merchant));

        MerchantProfileResponse response = merchantProfileService.getProfile(merchantUserId);

        assertNotNull(response);
        assertEquals(merchantUserId, response.getUserId());
        assertEquals("Bui Thi Chu Bai", response.getFullName());
        assertEquals("********9999", response.getMaskedCitizenId());
        assertEquals("********4555", response.getMaskedBankAccount());
        assertEquals("VERIFIED", response.getVerificationStatus());
    }

    @Test
    @DisplayName("Get profile throws NOT_FOUND when merchant not found")
    void testGetProfileNotFound() {
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> merchantProfileService.getProfile(merchantUserId));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Update profile fails when email is already taken by another user")
    void testUpdateProfileEmailConflict() {
        User user = User.builder().id(merchantUserId).email("old@example.com").build();
        Merchant merchant = Merchant.builder().id(15L).user(user).build();
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(merchant));

        User otherUser = User.builder().id(999L).email("taken@example.com").build();
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(otherUser));

        UpdateMerchantProfileRequest req = new UpdateMerchantProfileRequest();
        req.setEmail("taken@example.com");

        AppException ex = assertThrows(AppException.class, () -> merchantProfileService.updateProfile(merchantUserId, req));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Upload document fails when content type is not allowed")
    void testUploadDocumentInvalidContentType() {
        User user = User.builder().id(merchantUserId).build();
        Merchant merchant = Merchant.builder().id(15L).user(user).build();
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(merchant));

        MockMultipartFile file = new MockMultipartFile(
                "file", "script.sh", "application/x-sh", "echo 'hello'".getBytes()
        );

        AppException ex = assertThrows(AppException.class, () ->
                merchantProfileService.uploadDocument(merchantUserId, "GPKD-123", file));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}
