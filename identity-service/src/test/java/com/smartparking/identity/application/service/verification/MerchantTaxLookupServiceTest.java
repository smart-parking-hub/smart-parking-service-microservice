package com.smartparking.identity.application.service.verification;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.verification.AdminTaxLookupRequest;
import com.smartparking.identity.domain.model.verification.VerificationStatus;
import com.smartparking.identity.domain.repository.MerchantVerificationDossierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantTaxLookupServiceTest {
    private static final AdminTaxLookupRequest CONFIRM_BUSINESS = new AdminTaxLookupRequest(true, false);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "GPKD-123", "0316794479001", "0316794479-01"})
    void rejectsValuesThatDoNotMatchTheConfirmedBusinessFormat(String registrationNumber) {
        var dossiers = mock(MerchantVerificationDossierRepository.class);
        var target = mock(MerchantVerificationDossierRepository.TaxLookupTarget.class);
        when(dossiers.findTaxLookupTarget(1L)).thenReturn(Optional.of(target));
        when(target.getSubmissionVersion()).thenReturn(1);
        when(target.getStatus()).thenReturn(VerificationStatus.PENDING_REVIEW);
        when(target.getBusinessRegistrationNumber()).thenReturn(registrationNumber);
        var registry = mock(BusinessTaxRegistry.class);
        var service = new MerchantTaxLookupService(dossiers, registry, Duration.ZERO);

        assertThatThrownBy(() -> service.lookup(1L, 1, 5L, CONFIRM_BUSINESS))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(registry);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void routesTwelveDigitNumbersToManualReview(boolean confirmedBusiness) {
        var dossiers = mock(MerchantVerificationDossierRepository.class);
        var target = mock(MerchantVerificationDossierRepository.TaxLookupTarget.class);
        when(dossiers.findTaxLookupTarget(1L)).thenReturn(Optional.of(target));
        when(target.getSubmissionVersion()).thenReturn(1);
        when(target.getStatus()).thenReturn(VerificationStatus.PENDING_REVIEW);
        when(target.getBusinessRegistrationNumber()).thenReturn(" 012345678901 ");
        var registry = mock(BusinessTaxRegistry.class);
        var service = new MerchantTaxLookupService(dossiers, registry, Duration.ZERO);
        var confirmation = new AdminTaxLookupRequest(confirmedBusiness, !confirmedBusiness);

        assertThatThrownBy(() -> service.lookup(1L, 1, 5L, confirmation))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(error.getMessage()).contains("đối chiếu trực tiếp trên giấy tờ");
                });
        verifyNoInteractions(registry);
    }

    @Test
    void discardsProviderResultWhenReviewEndsDuringTheLookup() {
        var dossiers = mock(MerchantVerificationDossierRepository.class);
        var target = mock(MerchantVerificationDossierRepository.TaxLookupTarget.class);
        when(dossiers.findTaxLookupTarget(1L)).thenReturn(Optional.of(target));
        when(target.getSubmissionVersion()).thenReturn(1);
        when(target.getStatus()).thenReturn(VerificationStatus.PENDING_REVIEW);
        when(target.getBusinessRegistrationNumber()).thenReturn("0316794479");
        var registry = mock(BusinessTaxRegistry.class);
        when(registry.isEnabled()).thenReturn(true);
        when(registry.lookup("0316794479")).thenAnswer(invocation -> {
            when(target.getStatus()).thenReturn(VerificationStatus.VERIFIED);
            return BusinessTaxRegistry.Result.found(new BusinessTaxRegistry.Business("0316794479", "CASSO", null, null, false));
        });
        var service = new MerchantTaxLookupService(dossiers, registry, Duration.ZERO);

        assertThatThrownBy(() -> service.lookup(1L, 1, 5L, CONFIRM_BUSINESS))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
