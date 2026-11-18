package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.verification.DocumentType;
import com.smartparking.identity.domain.model.verification.MerchantVerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MerchantVerificationDocumentRepository extends JpaRepository<MerchantVerificationDocument, String> {

    List<MerchantVerificationDocument> findByDossierIdAndInDraftTrue(Long dossierId);

    List<MerchantVerificationDocument> findByDossierIdAndDocumentTypeAndInDraftTrue(Long dossierId, DocumentType documentType);

    @Modifying
    @Query("UPDATE MerchantVerificationDocument d SET d.inDraft = false WHERE d.dossier.id = :dossierId AND d.documentType = :documentType AND d.inDraft = true")
    void markDraftDocumentsInactive(@Param("dossierId") Long dossierId, @Param("documentType") DocumentType documentType);
}
