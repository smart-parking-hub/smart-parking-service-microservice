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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
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

    @TempDir
    Path tempDir;

    private final Long merchantUserId = 200L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(merchantProfileService, "documentsDirectory", tempDir.toString());
    }

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
    @DisplayName("Update profile updates all fields successfully")
    void testUpdateProfileSuccess() {
        User user = User.builder().id(merchantUserId).email("old@example.com").build();
        Merchant merchant = Merchant.builder().id(15L).user(user).build();
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(merchant));

        UpdateMerchantProfileRequest req = new UpdateMerchantProfileRequest();
        req.setFullName("Nguyen Van Owner");
        req.setEmail("new@example.com");
        req.setAddress("456 Nguyen Trai, Q5");
        req.setEmergencyContact("0911223344");
        req.setCitizenId("012345678901");
        req.setBusinessLicense("GPKD-8888");
        req.setBankName("Vietcombank");
        req.setBankAccountNum("9988776655");

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(merchantRepository.findByCitizenId("012345678901")).thenReturn(Optional.empty());

        MerchantProfileResponse resp = merchantProfileService.updateProfile(merchantUserId, req);

        assertNotNull(resp);
        assertEquals("Nguyen Van Owner", resp.getFullName());
        assertEquals("new@example.com", resp.getEmail());
        assertEquals("GPKD-8888", resp.getBusinessLicense());
        verify(userRepository, times(1)).save(user);
        verify(merchantRepository, times(1)).save(merchant);
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
    @DisplayName("Update profile fails when citizenId is already taken")
    void testUpdateProfileCitizenIdConflict() {
        User user = User.builder().id(merchantUserId).build();
        Merchant merchant = Merchant.builder().id(15L).user(user).build();
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(merchant));

        Merchant otherMerchant = Merchant.builder().id(99L).build();
        when(merchantRepository.findByCitizenId("012345678999")).thenReturn(Optional.of(otherMerchant));

        UpdateMerchantProfileRequest req = new UpdateMerchantProfileRequest();
        req.setCitizenId("012345678999");

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

    @Test
    @DisplayName("Upload document fails when file size exceeds 5MB")
    void testUploadDocumentFileTooLarge() {
        User user = User.builder().id(merchantUserId).build();
        Merchant merchant = Merchant.builder().id(15L).user(user).build();
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(merchant));

        byte[] bigData = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", bigData
        );

        AppException ex = assertThrows(AppException.class, () ->
                merchantProfileService.uploadDocument(merchantUserId, "GPKD-123", file));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Upload document successfully with valid PDF")
    void testUploadDocumentSuccess() {
        User user = User.builder().id(merchantUserId).fullName("Chu Bai").build();
        Merchant merchant = Merchant.builder().id(15L).user(user).build();
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(merchant));

        MockMultipartFile file = new MockMultipartFile(
                "file", "license.pdf", "application/pdf", "dummy pdf content".getBytes()
        );

        MerchantProfileResponse response =
                merchantProfileService.uploadDocument(merchantUserId, "GPKD-9999", file);

        assertNotNull(response);
        assertEquals("GPKD-9999", response.getBusinessLicense());
        verify(merchantRepository, times(1)).save(merchant);
    }
}
