package com.smartparking.identity.application.service.verification;

import com.smartparking.identity.domain.model.verification.AssessmentStatus;
import com.smartparking.identity.domain.model.verification.MerchantVerificationSubmission;
import com.smartparking.identity.domain.repository.MerchantVerificationDossierRepository;
import com.smartparking.identity.domain.repository.MerchantVerificationSubmissionRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class MerchantVerificationAssessmentWorker {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LEASE = Duration.ofSeconds(90);
    private static final Duration DEADLINE = Duration.ofMinutes(15);

    private final MerchantVerificationDossierRepository dossiers;
    private final MerchantVerificationSubmissionRepository submissions;
    private final MerchantVerificationAssessmentService assessor;
    private final TransactionTemplate transaction;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("merchant-verification-assessment").factory());
    private final AtomicBoolean running = new AtomicBoolean();

    @Value("${app.verification.assessment-worker-enabled:true}")
    private boolean enabled;

    public MerchantVerificationAssessmentWorker(
            MerchantVerificationDossierRepository dossiers,
            MerchantVerificationSubmissionRepository submissions,
            MerchantVerificationAssessmentService assessor,
            PlatformTransactionManager transactionManager) {
        this.dossiers = dossiers;
        this.submissions = submissions;
        this.assessor = assessor;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.verification.assessment-poll-ms:2000}")
    void poll() {
        if (enabled && running.compareAndSet(false, true)) {
            executor.execute(() -> {
                try {
                    processNext();
                } catch (RuntimeException error) {
                    log.warn("Merchant verification assessment will retry after its lease expires ({})",
                            error.getClass().getSimpleName());
                } finally {
                    running.set(false);
                }
            });
        }
    }

    public void processNext() {
        var candidates = submissions.findAssessmentCandidates(LocalDateTime.now(), PageRequest.of(0, 4));
        for (var candidate : candidates) {
            Claim claim = transaction.execute(status -> claim(candidate));
            if (claim == null) {
                continue;
            }

            // Only the detached submission and immutable document metadata are used outside the transaction.
            var result = assessor.assess(claim.submission(), claim.expired());
            transaction.executeWithoutResult(status -> complete(claim, result));
            return;
        }
    }

    private Claim claim(MerchantVerificationSubmissionRepository.AssessmentCandidate candidate) {
        if (dossiers.findByIdForUpdate(candidate.getDossierId()).isEmpty()) {
            return null;
        }
        var submission = submissions.findById(candidate.getId()).orElse(null);
        var now = LocalDateTime.now();
        if (submission == null || submission.getAssessmentStatus() != AssessmentStatus.PENDING
                || (submission.getAssessmentLeaseUntil() != null && submission.getAssessmentLeaseUntil().isAfter(now))) {
            return null;
        }

        boolean expired = submission.getAssessmentAttempts() >= MAX_ATTEMPTS
                || !now.isBefore(submission.getSubmittedAt().plus(DEADLINE));
        submission.setAssessmentAttempts(submission.getAssessmentAttempts() + 1);
        submission.setAssessmentLeaseUntil(now.plus(LEASE));
        var snapshot = MerchantVerificationSubmission.builder()
                .id(submission.getId())
                .businessRegistrationNumber(submission.getBusinessRegistrationNumber())
                .legalName(submission.getLegalName())
                .ocrDocumentId(submission.getOcrDocumentId())
                .documents(new HashSet<>(submission.getDocuments()))
                .build();
        return new Claim(candidate.getDossierId(), snapshot, submission.getAssessmentAttempts(), expired);
    }

    private void complete(Claim claim, MerchantVerificationAssessmentService.AssessmentResult result) {
        if (dossiers.findByIdForUpdate(claim.dossierId()).isEmpty()) {
            return;
        }
        var submission = submissions.findById(claim.submission().getId()).orElse(null);
        var now = LocalDateTime.now();
        if (submission == null || submission.getAssessmentStatus() != AssessmentStatus.PENDING
                || submission.getAssessmentAttempts() != claim.attempt()
                || !submission.getAssessmentLeaseUntil().isAfter(now)) {
            return;
        }

        // A late result is scoped to its own immutable submission, including after a decision or resubmission.
        submission.getAssessmentChecks().clear();
        for (var check : result.checks()) {
            check.setSubmission(submission);
            submission.getAssessmentChecks().add(check);
        }
        submission.setAssessmentStatus(result.status());
        if (result.method() != null) {
            submission.setOcrDocumentId(result.documentId());
            submission.setOcrMethod(result.method());
        }
        submission.setAssessmentCheckedAt(now);
        submission.setAssessmentLeaseUntil(null);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    private record Claim(Long dossierId, MerchantVerificationSubmission submission, int attempt, boolean expired) {}
}
