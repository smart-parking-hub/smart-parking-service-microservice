package com.smartparking.payment.application.service;

import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnpaidHoldAssessmentService {
    public enum Outcome { UNPAID, PROTECTED, UNKNOWN }
    private final PaymentRepository payments;
    private final PaymentAttemptGuard guard;
    private final BookingPaymentVerifier bookings;
    private final PayOS payOS;
    private final CircuitBreakerFactory<?,?> breakers;

    @Transactional
    public Outcome assess(Long bookingId) {
        guard.lock(bookingId,PaymentTypeEnum.DEPOSIT);
        var state=breakers.create("booking-unpaid-hold-state").run(() -> bookings.holdState(bookingId),failure -> null);
        if (state==null || !Objects.equals(state.bookingId(),bookingId) || state.driverId()==null) return Outcome.UNKNOWN;
        if (!state.unpaidReleaseCandidate() || !("EXPIRED".equals(state.status()) || "CANCELLED".equals(state.status()))) return Outcome.PROTECTED;
        var attempts=payments.findAttemptsForUpdate(bookingId,PaymentTypeEnum.DEPOSIT);
        // Group lock fences create/webhook; a later create must re-read the terminal booking quote.
        for (Payment payment:attempts) {
            if (!state.driverId().equals(payment.getDriverId()) || payment.getStatus()==PaymentStatusEnum.SUCCESS
                    || payment.getStatus()==PaymentStatusEnum.REFUNDED || payment.getStatus()==PaymentStatusEnum.REQUIRES_REVIEW)
                return Outcome.PROTECTED;
        }
        boolean unknown=false;
        for (Payment payment:attempts) {
            if (payment.getGateway()==PaymentGatewayEnum.WALLET) {
                if (payment.getStatus()==PaymentStatusEnum.FAILED) continue;
                return Outcome.PROTECTED;
            }
            if (payment.getGateway()!=PaymentGatewayEnum.PAYOS || payment.getOrderCode()==null) { unknown=true; continue; }
            try {
                PaymentLink link=payOS.paymentRequests().get(payment.getOrderCode());
                if (!matches(payment,link) || link.getAmountPaid()==null || link.getAmountPaid()<0) { unknown=true; continue; }
                if (link.getAmountPaid()>0 || link.getStatus()==PaymentLinkStatus.PAID || link.getStatus()==PaymentLinkStatus.UNDERPAID) {
                    protect(payment); return Outcome.PROTECTED;
                }
                if (link.getStatus()==PaymentLinkStatus.PENDING) {
                    link=payOS.paymentRequests().cancel(payment.getOrderCode(),"Lượt giữ chỗ chưa thanh toán đã kết thúc");
                    if (!matches(payment,link) || link.getAmountPaid()==null || link.getAmountPaid()<0) { unknown=true; continue; }
                    if (link.getAmountPaid()>0 || link.getStatus()==PaymentLinkStatus.PAID || link.getStatus()==PaymentLinkStatus.UNDERPAID) {
                        protect(payment); return Outcome.PROTECTED;
                    }
                }
                if (link.getAmountPaid()==0 && (link.getStatus()==PaymentLinkStatus.CANCELLED || link.getStatus()==PaymentLinkStatus.EXPIRED)) {
                    if (payment.getStatus()==PaymentStatusEnum.PENDING) { payment.setStatus(PaymentStatusEnum.FAILED); payments.save(payment); }
                } else unknown=true;
            } catch (Exception failure) { log.warn("Could not assess unpaid release for order {}",payment.getOrderCode()); unknown=true; }
        }
        return unknown ? Outcome.UNKNOWN : Outcome.UNPAID;
    }
    private boolean matches(Payment payment, PaymentLink link) {
        return link!=null && Objects.equals(link.getOrderCode(),payment.getOrderCode()) && link.getAmount()!=null
                && payment.getAmount()!=null && BigDecimal.valueOf(link.getAmount()).compareTo(payment.getAmount())==0;
    }
    private void protect(Payment payment) {
        payment.setStatus(PaymentStatusEnum.REQUIRES_REVIEW);
        payment.setReviewReason("Vé đã kết thúc nhưng có thông tin nhận tiền; cần đối soát, không tính bỏ giữ chỗ");
        payments.save(payment);
    }
}
