package com.smartparking.identity.application.service.verification;

import com.smartparking.identity.application.dto.verification.*;
import com.smartparking.identity.domain.model.verification.VerificationStatus;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MerchantVerificationService {

    MerchantVerificationDetailResponse getMerchantVerification(Long merchantUserId);

    MerchantVerificationDetailResponse updateVerificationDetails(Long merchantUserId, UpdateVerificationDetailsRequest request);

    MerchantVerificationDetailResponse replaceDocuments(Long merchantUserId, String documentType, Long draftRevision, List<MultipartFile> files);

    MerchantVerificationDetailResponse deleteDocuments(Long merchantUserId, String documentType, Long draftRevision);

    MerchantVerificationDetailResponse submitVerification(Long merchantUserId, SubmitVerificationRequest request);

    DocumentContentResponse getMerchantDocumentContent(Long merchantUserId, String documentId);

    Page<AdminVerificationSummaryResponse> getAdminVerifications(VerificationStatus status, Pageable pageable);

    MerchantVerificationDetailResponse getAdminVerificationDetail(Long dossierId);

    DocumentContentResponse getAdminDocumentContent(Long dossierId, Integer submissionVersion, String documentId);

    MerchantVerificationDetailResponse adminApprove(Long dossierId, CustomUserDetails adminUser, AdminApproveVerificationRequest request);

    MerchantVerificationDetailResponse adminRequestChanges(Long dossierId, CustomUserDetails adminUser, AdminRequestChangesRequest request);

    record DocumentContentResponse(byte[] content, String contentType, String originalName) {}
}
