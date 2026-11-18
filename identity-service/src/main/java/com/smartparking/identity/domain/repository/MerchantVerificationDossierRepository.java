package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.verification.MerchantVerificationDossier;
import com.smartparking.identity.domain.model.verification.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MerchantVerificationDossierRepository extends JpaRepository<MerchantVerificationDossier, Long> {

    interface TaxLookupTarget {
        Integer getSubmissionVersion();
        VerificationStatus getStatus();
        String getBusinessRegistrationNumber();
    }

    @Query("""
            SELECT d.status AS status, s.submissionVersion AS submissionVersion,
                   s.businessRegistrationNumber AS businessRegistrationNumber
            FROM MerchantVerificationDossier d
            LEFT JOIN MerchantVerificationSubmission s ON s.dossier = d AND s.submissionVersion = d.submissionVersion
            WHERE d.id = :id
            """)
    Optional<TaxLookupTarget> findTaxLookupTarget(@Param("id") Long id);

    Optional<MerchantVerificationDossier> findByMerchantId(Long merchantId);

    Optional<MerchantVerificationDossier> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM MerchantVerificationDossier d WHERE d.id = :id")
    Optional<MerchantVerificationDossier> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM MerchantVerificationDossier d WHERE d.merchant.id = :merchantId")
    Optional<MerchantVerificationDossier> findByMerchantIdForUpdate(@Param("merchantId") Long merchantId);

    @Query("SELECT d.merchant.id FROM MerchantVerificationDossier d WHERE d.id = :id")
    Optional<Long> findMerchantId(@Param("id") Long id);
}
