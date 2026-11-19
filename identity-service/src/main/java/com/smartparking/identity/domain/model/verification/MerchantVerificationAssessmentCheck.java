package com.smartparking.identity.domain.model.verification;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "merchant_verification_assessment_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantVerificationAssessmentCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private MerchantVerificationSubmission submission;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(length = 50)
    private String field;

    @Column(name = "declared_value", length = 255)
    private String declaredValue;

    @Column(name = "extracted_value", length = 255)
    private String extractedValue;

    @Column(nullable = false, length = 500)
    private String message;
}
