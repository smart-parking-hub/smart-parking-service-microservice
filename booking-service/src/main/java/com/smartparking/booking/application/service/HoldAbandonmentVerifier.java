package com.smartparking.booking.application.service;

import com.smartparking.booking.infrastructure.client.HoldPaymentAssessmentClient;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HoldAbandonmentVerifier {
    private final HoldPolicyStore store;
    private final HoldPaymentAssessmentClient payments;
    @Transactional
    public String verify(Long bookingId, LocalDateTime now) {
        var release=store.releaseForUpdate(bookingId).orElse(null);
        if (release==null) return "UNKNOWN";
        if ("PROTECTED".equals(release.assessment())) return "PROTECTED";
        var outcome=payments.assess(bookingId);
        String assessment=outcome==null ? "UNKNOWN" : outcome.name();
        store.assess(bookingId,assessment,now);
        return assessment;
    }
}
