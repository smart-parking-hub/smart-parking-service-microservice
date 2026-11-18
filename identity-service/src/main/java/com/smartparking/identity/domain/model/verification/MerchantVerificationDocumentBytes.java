package com.smartparking.identity.domain.model.verification;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "merchant_verification_document_bytes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantVerificationDocumentBytes {

    @Id
    @Column(name = "document_id", length = 64)
    private String documentId;

    @Lob
    @Column(name = "file_bytes", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] fileBytes;
}
