package com.smartparking.identity.application.service.verification;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.verification.AdminTaxLookupRequest;
import com.smartparking.identity.application.dto.verification.AdminTaxLookupResponse;
import com.smartparking.identity.domain.model.verification.VerificationStatus;
import com.smartparking.identity.domain.repository.MerchantVerificationDossierRepository;
import com.smartparking.identity.domain.repository.MerchantVerificationDossierRepository.TaxLookupTarget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class MerchantTaxLookupService {
    private static final Pattern BUSINESS_TAX_CODE = Pattern.compile("[0-9]{10}(?:-[0-9]{3})?");
    private static final Pattern TWELVE_DIGIT_REGISTRATION_NUMBER = Pattern.compile("[0-9]{12}");

    private final MerchantVerificationDossierRepository dossiers;
    private final BusinessTaxRegistry registry;
    private final Duration minimumInterval;
    private final Map<Long, Instant> adminCooldowns = new HashMap<>();
    private final Map<Long, Instant> dossierCooldowns = new HashMap<>();

    public MerchantTaxLookupService(MerchantVerificationDossierRepository dossiers, BusinessTaxRegistry registry,
                                    @Value("${app.verification.casso.minimum-interval:3s}") Duration minimumInterval) {
        this.dossiers = dossiers;
        this.registry = registry;
        this.minimumInterval = minimumInterval;
    }

    public AdminTaxLookupResponse lookup(Long dossierId, Integer version, Long adminId, AdminTaxLookupRequest request) {
        if (!request.confirmedBusinessTaxCode() && !request.confirmedHouseholdTaxCode()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Vui lòng xác nhận đây là mã số thuế doanh nghiệp hoặc chi nhánh trên giấy tờ.");
        }
        var target = requireCurrentSubmission(dossierId, version);
        String declaredNumber = target.getBusinessRegistrationNumber();
        String taxCode = declaredNumber == null ? "" : declaredNumber.strip();
        if (request.confirmedHouseholdTaxCode() || TWELVE_DIGIT_REGISTRATION_NUMBER.matcher(taxCode).matches()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng đối chiếu trực tiếp trên giấy tờ.");
        }
        if (!BUSINESS_TAX_CODE.matcher(taxCode).matches()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã số đã khai không đúng định dạng MST doanh nghiệp hoặc chi nhánh.");
        }

        var result = BusinessTaxRegistry.Result.unavailable(null);
        if (registry.isEnabled()) {
            Instant retryAt = reserveLookup(adminId, dossierId, Instant.now());
            if (retryAt == null) {
                result = registry.lookup(taxCode);
                requireCurrentSubmission(dossierId, version);
            } else {
                result = BusinessTaxRegistry.Result.unavailable(retryAt);
            }
        }
        return new AdminTaxLookupResponse(dossierId, version, result.outcome(), Instant.now(), result.business(), result.retryAt());
    }

    private TaxLookupTarget requireCurrentSubmission(Long dossierId, Integer version) {
        var target = dossiers.findTaxLookupTarget(dossierId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác minh đã nộp."));
        if (target.getSubmissionVersion() == null || target.getStatus() == VerificationStatus.DRAFT) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác minh đã nộp.");
        }
        if (!version.equals(target.getSubmissionVersion()) || target.getStatus() != VerificationStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.CONFLICT, "Hồ sơ đã thay đổi. Vui lòng tải lại trước khi tra cứu.");
        }
        return target;
    }

    private synchronized Instant reserveLookup(Long adminId, Long dossierId, Instant now) {
        adminCooldowns.values().removeIf(until -> !now.isBefore(until));
        dossierCooldowns.values().removeIf(until -> !now.isBefore(until));
        Instant adminUntil = adminCooldowns.getOrDefault(adminId, now);
        Instant dossierUntil = dossierCooldowns.getOrDefault(dossierId, now);
        Instant retryAt = adminUntil.isAfter(dossierUntil) ? adminUntil : dossierUntil;
        if (retryAt.isAfter(now)) {
            return retryAt;
        }
        Instant nextLookup = now.plus(minimumInterval);
        adminCooldowns.put(adminId, nextLookup);
        dossierCooldowns.put(dossierId, nextLookup);
        return null;
    }
}
