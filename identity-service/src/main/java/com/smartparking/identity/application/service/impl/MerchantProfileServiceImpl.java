package com.smartparking.identity.application.service.impl;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.MerchantProfileResponse;
import com.smartparking.identity.application.dto.UpdateMerchantProfileRequest;
import com.smartparking.identity.application.service.MerchantProfileService;
import com.smartparking.identity.domain.model.Merchant;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.MerchantRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantProfileServiceImpl implements MerchantProfileService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;

    @Value("${app.storage.merchant-documents-dir:uploads/merchant-documents}")
    private String documentsDirectory;

    @Override
    @Transactional(readOnly = true)
    public MerchantProfileResponse getProfile(Long merchantUserId) {
        return toResponse(requireMerchant(merchantUserId));
    }

    @Override
    @Transactional
    public MerchantProfileResponse updateProfile(Long merchantUserId, UpdateMerchantProfileRequest request) {
        Merchant merchant = requireMerchant(merchantUserId);
        User user = merchant.getUser();

        if (request.getFullName() != null) {
            String name = request.getFullName().trim();
            user.setFullName(name.isEmpty() ? null : name);
        }
        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            if (!email.isEmpty()) {
                userRepository.findByEmail(email).ifPresent(existing -> {
                    if (!existing.getId().equals(user.getId())) {
                        throw new AppException(ErrorCode.CONFLICT, "Email đã được sử dụng bởi tài khoản khác!");
                    }
                });
                user.setEmail(email);
            } else {
                user.setEmail(null);
            }
        }
        if (request.getAddress() != null) {
            user.setAddress(blankToNull(request.getAddress()));
        }
        if (request.getEmergencyContact() != null) {
            user.setEmergencyContact(blankToNull(request.getEmergencyContact()));
        }
        if (request.getCitizenId() != null && !request.getCitizenId().isBlank()) {
            String citizenId = request.getCitizenId().trim();
            merchantRepository.findByCitizenId(citizenId).ifPresent(existing -> {
                if (!existing.getId().equals(merchant.getId())) {
                    throw new AppException(ErrorCode.CONFLICT, "CCCD/CMND đã được sử dụng!");
                }
            });
            merchant.setCitizenId(citizenId);
        }
        if (request.getBusinessLicense() != null) {
            merchant.setBusinessLicense(blankToNull(request.getBusinessLicense()));
        }
        if (request.getBankName() != null && !request.getBankName().isBlank()) {
            merchant.setBankName(request.getBankName().trim());
        }
        if (request.getBankAccountNum() != null && !request.getBankAccountNum().isBlank()) {
            merchant.setBankAccountNum(request.getBankAccountNum().trim());
        }

        userRepository.save(user);
        merchantRepository.save(merchant);

        return toResponse(merchant);
    }

    @Override
    @Transactional
    public MerchantProfileResponse uploadDocument(Long merchantUserId, String businessLicense, MultipartFile file) {
        Merchant merchant = requireMerchant(merchantUserId);

        if (businessLicense != null && !businessLicense.isBlank()) {
            merchant.setBusinessLicense(businessLicense.trim());
        }

        if (file != null && !file.isEmpty()) {
            validateFile(file);
            try {
                Path dir = Paths.get(documentsDirectory);
                Files.createDirectories(dir);

                String ext = extensionOf(file.getOriginalFilename());
                String filename = "merchant_" + merchant.getId() + "_" + UUID.randomUUID() + ext;
                Path target = dir.resolve(filename);
                file.transferTo(target);

                log.info("Uploaded document for merchant {}: {}", merchant.getId(), target);
            } catch (IOException e) {
                log.error("Failed to store merchant document: {}", e.getMessage());
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lưu tệp đính kèm!");
            }
        }

        merchantRepository.save(merchant);
        return toResponse(merchant);
    }

    private Merchant requireMerchant(Long userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ chủ bãi xe!"));
    }

    private MerchantProfileResponse toResponse(Merchant merchant) {
        User user = merchant.getUser();
        String citizenId = merchant.getCitizenId();
        String bankAccount = merchant.getBankAccountNum();

        String verificationStatus = (merchant.getBusinessLicense() != null && !merchant.getBusinessLicense().isBlank())
                ? "VERIFIED" : "PENDING";
        String verificationLabel = "VERIFIED".equals(verificationStatus) ? "Đã xác thực" : "Chờ xác thực";

        return MerchantProfileResponse.builder()
                .merchantId(merchant.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .citizenId(citizenId)
                .maskedCitizenId(mask(citizenId, 4))
                .businessLicense(merchant.getBusinessLicense())
                .bankName(merchant.getBankName())
                .bankAccountNum(bankAccount)
                .maskedBankAccount(mask(bankAccount, 4))
                .walletBalance(merchant.getWalletBalance() != null ? merchant.getWalletBalance() : BigDecimal.ZERO)
                .accountStatus(user.getStatus())
                .verificationStatus(verificationStatus)
                .verificationLabel(verificationLabel)
                .address(user.getAddress())
                .emergencyContact(user.getEmergencyContact())
                .build();
    }

    private void validateFile(MultipartFile file) {
        String rawContentType = file.getContentType();
        String contentType = rawContentType != null ? rawContentType.toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ chấp nhận PDF hoặc ảnh JPEG/PNG/WEBP!");
        }
        if (file.getSize() > 5 * 1024 * 1024L) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File tối đa 5MB!");
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        if (idx < 0) return "";
        String ext = filename.substring(idx).toLowerCase(Locale.ROOT);
        if (ext.length() > 10) return "";
        return ext;
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String mask(String value, int visibleSuffix) {
        if (value == null || value.isBlank()) return null;
        int keep = Math.min(Math.max(visibleSuffix, 0), value.length());
        return "*".repeat(Math.max(0, value.length() - keep)) + value.substring(value.length() - keep);
    }
}
