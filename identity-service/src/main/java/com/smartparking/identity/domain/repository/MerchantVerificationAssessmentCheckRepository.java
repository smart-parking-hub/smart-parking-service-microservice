package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.verification.MerchantVerificationAssessmentCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MerchantVerificationAssessmentCheckRepository extends JpaRepository<MerchantVerificationAssessmentCheck, Long> {
    List<MerchantVerificationAssessmentCheck> findBySubmissionId(Long submissionId);
}
