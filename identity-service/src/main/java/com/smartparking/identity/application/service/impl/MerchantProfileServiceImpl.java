package com.smartparking.identity.application.service.impl;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.MerchantProfileResponse;
import com.smartparking.identity.application.dto.UpdateMerchantProfileRequest;
import com.smartparking.identity.application.service.MerchantProfileService;
import com.smartparking.identity.application.service.verification.VerificationDocumentStorage;
import com.smartparking.identity.domain.model.Merchant;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.model.verification.DocumentType;
import com.smartparking.identity.domain.model.verification.MerchantVerificationDossier;
import com.smartparking.identity.domain.model.verification.VerificationStatus;
import com.smartparking.identity.domain.repository.MerchantRepository;
import com.smartparking.identity.domain.repository.MerchantVerificationDossierRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantProfileServiceImpl implements MerchantProfileService {

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final MerchantVerificationDossierRepository dossierRepository;
    private final VerificationDocumentStorage documentStorage;

    @Override
    @Transactional(readOnly = true)
    public MerchantProfileResponse getProfile(Long merchantUserId) {
        return toResponse(requireMerchant(merchantUserId));
    }

    @Override
    @Transactional
    public MerchantProfileResponse updateProfile(Long merchantUserId, UpdateMerchantProfileRequest request) {
        Merchant merchant = merchantRepository.findByUserIdForUpdate(merchantUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ chủ bãi xe!"));
        User user = merchant.getUser();

        // 1. Pre-validate banking fields if present (atomic / no partial update)
        String trimmedBankName = null;
        if (request.getBankName() != null) {
            trimmedBankName = request.getBankName().trim();
            if (trimmedBankName.isEmpty() || trimmedBankName.length() > 100) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tên ngân hàng phải từ 1 đến 100 ký tự!");
            }
        }

        String trimmedBankAccountNum = null;
        if (request.getBankAccountNum() != null) {
            trimmedBankAccountNum = request.getBankAccountNum().trim();
            if (!trimmedBankAccountNum.matches("^\\d{6,30}$")) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Số tài khoản phải gồm 6–30 chữ số!");
            }
        }

        String trimmedAccountHolderName = null;
        if (request.getAccountHolderName() != null) {
            trimmedAccountHolderName = request.getAccountHolderName().trim();
            if (trimmedAccountHolderName.isEmpty() || trimmedAccountHolderName.length() > 100) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tên chủ tài khoản phải từ 1 đến 100 ký tự!");
            }
        }

        // 2. Validate and update user contact / license fields
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
        if (request.getBusinessLicense() != null && dossierRepository.findByMerchantId(merchant.getId()).isEmpty()) {
            merchant.setBusinessLicense(blankToNull(request.getBusinessLicense()));
        }

        // 3. Apply validated banking fields (patch-like: null/absent retains existing)
        if (trimmedBankName != null) {
            merchant.setBankName(trimmedBankName);
        }
        if (trimmedBankAccountNum != null) {
            merchant.setBankAccountNum(trimmedBankAccountNum);
        }
        if (trimmedAccountHolderName != null) {
            merchant.setAccountHolderName(trimmedAccountHolderName);
        }

        userRepository.save(user);
        merchantRepository.save(merchant);

        return toResponse(merchant);
    }

    @Override
    @Transactional
    public MerchantProfileResponse uploadDocument(Long merchantUserId, String businessLicense, MultipartFile file) {
        Merchant merchant = merchantRepository.findByUserIdForUpdate(merchantUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ chủ bãi xe!"));

        MerchantVerificationDossier dossier = dossierRepository.findByMerchantIdForUpdate(merchant.getId())
                .orElseGet(() -> {
                    MerchantVerificationDossier newDossier = MerchantVerificationDossier.builder()
                            .merchant(merchant)
                            .user(merchant.getUser())
                            .status(VerificationStatus.DRAFT)
                            .draftRevision(1L)
                            .businessRegistrationNumber(merchant.getBusinessLicense())
                            .build();
                    return dossierRepository.save(newDossier);
                });

        if (dossier.getStatus() != VerificationStatus.DRAFT) {
            throw new AppException(ErrorCode.CONFLICT, "Chỉ có thể lưu giấy tờ qua hồ sơ cũ khi hồ sơ xác minh còn là bản nháp!");
        }

        if (businessLicense != null && !businessLicense.isBlank()) {
            String trimmed = businessLicense.trim();
            merchant.setBusinessLicense(trimmed);
            dossier.setBusinessRegistrationNumber(trimmed);
        }

        if (file != null) {
            documentStorage.replaceGroup(dossier, DocumentType.BUSINESS_REGISTRATION, List.of(file));
        }

        dossier.setDraftRevision(dossier.getDraftRevision() + 1);
        dossierRepository.save(dossier);
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

        Optional<MerchantVerificationDossier> dossierOpt = dossierRepository.findByMerchantId(merchant.getId());

        String verificationStatus;
        String verificationLabel;
        String businessLicense = merchant.getBusinessLicense();

        if (dossierOpt.isPresent()) {
            MerchantVerificationDossier dossier = dossierOpt.get();
            businessLicense = dossier.getBusinessRegistrationNumber();
            if (dossier.getStatus() == VerificationStatus.VERIFIED) {
                verificationStatus = "VERIFIED";
                verificationLabel = "Đã xác thực";
            } else if (dossier.getStatus() == VerificationStatus.PENDING_REVIEW) {
                verificationStatus = "PENDING";
                verificationLabel = "Chờ duyệt";
            } else if (dossier.getStatus() == VerificationStatus.CHANGES_REQUESTED) {
                verificationStatus = "CHANGES_REQUESTED";
                verificationLabel = "Cần bổ sung";
            } else {
                verificationStatus = "NOT_SUBMITTED";
                verificationLabel = "Chờ xác thực";
            }
        } else {
            verificationStatus = "NOT_SUBMITTED";
            verificationLabel = "Chờ xác thực";
        }

        return MerchantProfileResponse.builder()
                .merchantId(merchant.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .citizenId(citizenId)
                .maskedCitizenId(mask(citizenId, 4))
                .businessLicense(businessLicense)
                .bankName(merchant.getBankName())
                .bankAccountNum(bankAccount)
                .accountHolderName(merchant.getAccountHolderName())
                .maskedBankAccount(mask(bankAccount, 4))
                .walletBalance(merchant.getWalletBalance() != null ? merchant.getWalletBalance() : BigDecimal.ZERO)
                .accountStatus(user.getStatus())
                .verificationStatus(verificationStatus)
                .verificationLabel(verificationLabel)
                .address(user.getAddress())
                .emergencyContact(user.getEmergencyContact())
                .build();
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
