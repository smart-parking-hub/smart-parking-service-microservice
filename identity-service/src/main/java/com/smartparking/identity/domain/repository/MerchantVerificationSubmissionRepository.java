package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.verification.AssessmentStatus;
import com.smartparking.identity.domain.model.verification.MerchantVerificationSubmission;
import com.smartparking.identity.domain.model.verification.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MerchantVerificationSubmissionRepository extends JpaRepository<MerchantVerificationSubmission, Long> {

    interface AssessmentCandidate {
        Long getId();
        Long getDossierId();
    }

    @Query("""
            SELECT s.id AS id, s.dossier.id AS dossierId
            FROM MerchantVerificationSubmission s
            WHERE s.assessmentStatus = com.smartparking.identity.domain.model.verification.AssessmentStatus.PENDING
              AND (s.assessmentLeaseUntil IS NULL OR s.assessmentLeaseUntil <= :now)
            ORDER BY s.submittedAt, s.id
            """)
    List<AssessmentCandidate> findAssessmentCandidates(LocalDateTime now, Pageable pageable);

    interface AdminSummary {
        Long getId();
        Long getMerchantId();
        String getLegalName();
        String getBusinessRegistrationNumber();
        VerificationStatus getStatus();
        AssessmentStatus getAssessmentStatus();
        Integer getSubmissionVersion();
        LocalDateTime getSubmittedAt();
    }

    @Query("""
            SELECT d.id AS id, d.merchant.id AS merchantId, s.legalName AS legalName,
                   s.businessRegistrationNumber AS businessRegistrationNumber, d.status AS status,
                   s.assessmentStatus AS assessmentStatus, s.submissionVersion AS submissionVersion,
                   s.submittedAt AS submittedAt
            FROM MerchantVerificationSubmission s JOIN s.dossier d
            WHERE s.submissionVersion = d.submissionVersion
              AND d.status <> com.smartparking.identity.domain.model.verification.VerificationStatus.DRAFT
              AND (:status IS NULL OR d.status = :status)
            """)
    Page<AdminSummary> findAdminSummaries(@Param("status") VerificationStatus status, Pageable pageable);

    @Query("SELECT COUNT(s) > 0 FROM MerchantVerificationSubmission s JOIN s.documents d WHERE d.id = :id")
    boolean existsByDocumentId(@Param("id") String id);

    Optional<MerchantVerificationSubmission> findByDossierIdAndSubmissionVersion(Long dossierId, Integer submissionVersion);

    @Query("SELECT s FROM MerchantVerificationSubmission s WHERE s.dossier.id = :dossierId ORDER BY s.submissionVersion DESC LIMIT 1")
    Optional<MerchantVerificationSubmission> findLatestByDossierId(@Param("dossierId") Long dossierId);
}
