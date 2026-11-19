package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.verification.MerchantVerificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MerchantVerificationHistoryRepository extends JpaRepository<MerchantVerificationHistory, Long> {

    List<MerchantVerificationHistory> findByDossierIdOrderByOccurredAtAsc(Long dossierId);
}
