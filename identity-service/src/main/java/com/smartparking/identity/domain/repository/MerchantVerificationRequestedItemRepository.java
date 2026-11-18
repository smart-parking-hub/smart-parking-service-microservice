package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.verification.MerchantVerificationRequestedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MerchantVerificationRequestedItemRepository extends JpaRepository<MerchantVerificationRequestedItem, Long> {
    List<MerchantVerificationRequestedItem> findBySubmissionId(Long submissionId);
}
