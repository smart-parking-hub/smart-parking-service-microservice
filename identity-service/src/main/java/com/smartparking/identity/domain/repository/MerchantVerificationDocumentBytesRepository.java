package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.verification.MerchantVerificationDocumentBytes;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantVerificationDocumentBytesRepository extends JpaRepository<MerchantVerificationDocumentBytes, String> {
}
