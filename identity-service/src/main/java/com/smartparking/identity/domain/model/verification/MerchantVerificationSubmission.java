package com.smartparking.identity.domain.model.verification;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "merchant_verification_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantVerificationSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private MerchantVerificationDossier dossier;

    @Column(name = "submission_version", nullable = false)
    private Integer submissionVersion;

    @Column(name = "business_registration_number", nullable = false, length = 50)
    private String businessRegistrationNumber;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "resubmission_note", columnDefinition = "TEXT")
    private String resubmissionNote;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_status", nullable = false, length = 30)
    @Builder.Default
    private AssessmentStatus assessmentStatus = AssessmentStatus.NOT_STARTED;

    @Column(name = "assessment_checked_at")
    private LocalDateTime assessmentCheckedAt;

    @Column(name = "ocr_document_id", length = 64)
    private String ocrDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_method", length = 24)
    private OcrMethod ocrMethod;

    @Column(name = "assessment_attempts", nullable = false)
    private int assessmentAttempts;

    @Column(name = "assessment_lease_until")
    private LocalDateTime assessmentLeaseUntil;

    @Column(name = "decision_action", length = 30)
    private String decisionAction;

    @Column(name = "decision_actor", length = 100)
    private String decisionActor;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "decision_at")
    private LocalDateTime decisionAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "merchant_verification_submission_documents",
            joinColumns = @JoinColumn(name = "submission_id"),
            inverseJoinColumns = @JoinColumn(name = "document_id")
    )
    @Builder.Default
    private Set<MerchantVerificationDocument> documents = new HashSet<>();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MerchantVerificationAssessmentCheck> assessmentChecks = new ArrayList<>();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MerchantVerificationRequestedItem> requestedItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) submittedAt = LocalDateTime.now();
        if (assessmentStatus == null) assessmentStatus = AssessmentStatus.NOT_STARTED;
    }
}
