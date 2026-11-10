package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.event.BookingEvent;
import com.smartparking.payment.application.dto.RefundRequestDto;
import com.smartparking.payment.application.dto.SubmitRefundEvidenceRequest;
import com.smartparking.payment.application.dto.VerifyRefundEvidenceRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RefundService {

    RefundRequestDto handleBookingCancellationRefund(BookingEvent.BookingCancelledEvent event);

    RefundRequestDto submitTransferEvidence(Long merchantUserId, Long bookingId, SubmitRefundEvidenceRequest request);

    RefundRequestDto verifyTransferEvidence(Long adminUserId, Long refundId, VerifyRefundEvidenceRequest request);

    List<RefundRequestDto> reconcileOverdueRefunds();

    PageResponse<RefundRequestDto> getMerchantRefunds(Long merchantUserId, Pageable pageable);

    PageResponse<RefundRequestDto> getAllRefundsForAdmin(Pageable pageable);
}

