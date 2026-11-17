package com.smartparking.payment.application.service;

import com.smartparking.common.enums.*;
import com.smartparking.common.exception.*;
import com.smartparking.payment.application.dto.PaymentDto;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.Objects;
import static com.smartparking.payment.domain.model.LedgerAccountCode.*;

/** Reserve locally, confirm by durable booking receipt, then capture/release locally.
 * No database transaction spans the booking RPC; ambiguous outcomes remain recoverable. */
@Service
@Slf4j
public class DriverWalletPaymentService {
    private final DriverWalletLedger wallet;
    private final PaymentRepository payments;
    private final PaymentAttemptGuard guard;
    private final BookingPaymentVerifier booking;
    private final OutboxPublisherService outbox;
    private final TransactionTemplate tx;
    @Value("${app.driver-wallet-enabled:true}") private boolean enabled;

    public DriverWalletPaymentService(DriverWalletLedger wallet, PaymentRepository payments,
            PaymentAttemptGuard guard, BookingPaymentVerifier booking, OutboxPublisherService outbox,
            PlatformTransactionManager manager) {
        this.wallet = wallet; this.payments = payments; this.guard = guard;
        this.booking = booking; this.outbox = outbox; this.tx = new TransactionTemplate(manager);
    }
    private volatile long recoveryCursor;
    public boolean enabled() { return enabled; }

    public PaymentDto pay(Long driverId, Long bookingId) {
        if (!enabled) throw new AppException(ErrorCode.BAD_REQUEST, "Thanh toán ví đang tạm dừng");
        var quote = booking.require(bookingId, driverId);
        Payment payment = Objects.requireNonNull(tx.execute(status -> {
            guard.lock(bookingId, PaymentTypeEnum.DEPOSIT);
            var attempts = payments.findAttemptsForUpdate(bookingId, PaymentTypeEnum.DEPOSIT);
            for (var old : attempts) {
                if (!driverId.equals(old.getDriverId())) throw new AppException(ErrorCode.FORBIDDEN, "Giao dịch không thuộc tài khoản");
                if (old.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW) throw new AppException(ErrorCode.CONFLICT, "Giao dịch cần đối soát");
                if (old.getGateway() == PaymentGatewayEnum.WALLET) return old;
                if (old.getStatus() != PaymentStatusEnum.FAILED)
                    throw new AppException(ErrorCode.CONFLICT, "Đã có phiên PayOS; vui lòng kiểm tra phiên hiện tại trước khi dùng ví");
            }
            BigDecimal amount = quote.payable(PaymentTypeEnum.DEPOSIT);
            if (amount == null || amount.signum() <= 0) throw new AppException(ErrorCode.BAD_REQUEST, "Tiền cọc không hợp lệ");
            var created = payments.saveAndFlush(Payment.builder().bookingId(bookingId).driverId(driverId)
                    .merchantId(quote.merchantId()).parkingLotId(quote.parkingLotId()).amount(amount)
                    .gateway(PaymentGatewayEnum.WALLET).paymentType(PaymentTypeEnum.DEPOSIT)
                    .status(PaymentStatusEnum.PENDING).walletRefund(true).bookingExpiresAt(quote.expiresAt())
                    .idempotencyKey("wallet:booking:" + bookingId).transactionCode("WALLET-" + bookingId).build());
            wallet.post(driverId, bookingId, created.getId(), "WALLET:HOLD:" + created.getId(), "HOLD", amount,
                    DRIVER_WALLET_PAYABLE, DRIVER_WALLET_HOLD, amount.negate(), amount);
            return created;
        }));
        if (payment.getStatus() == PaymentStatusEnum.PENDING) recover(payment.getId());
        return get(driverId, payment.getId());
    }

    public PaymentDto get(Long driverId, Long paymentId) {
        var p = payments.findById(paymentId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch"));
        if (!driverId.equals(p.getDriverId())) throw new AppException(ErrorCode.FORBIDDEN, "Giao dịch không thuộc tài khoản");
        return PaymentDto.fromEntity(p);
    }

    public PaymentDto getForBooking(Long driverId, Long bookingId) {
        if (bookingId == null || bookingId <= 0) throw new AppException(ErrorCode.BAD_REQUEST, "Booking không hợp lệ");
        return payments.findFirstByDriverIdAndBookingIdAndGatewayOrderByIdDesc(driverId, bookingId, PaymentGatewayEnum.WALLET)
                .map(PaymentDto::fromEntity).orElse(null);
    }

    public void recover(Long paymentId) {
        var snapshot = payments.findById(paymentId).orElseThrow();
        if (snapshot.getGateway() != PaymentGatewayEnum.WALLET || snapshot.getStatus() != PaymentStatusEnum.PENDING) return;
        final boolean accepted;
        try { accepted = booking.confirmWallet(snapshot); }
        catch (RuntimeException failure) {
            log.warn("Wallet confirmation pending for payment {}; retained hold for recovery", paymentId);
            return;
        }
        tx.executeWithoutResult(status -> {
            guard.lock(snapshot.getBookingId(), PaymentTypeEnum.DEPOSIT);
            var payment = payments.findByIdForUpdate(paymentId).orElseThrow();
            if (payment.getStatus() != PaymentStatusEnum.PENDING) return;
            BigDecimal amount = payment.getAmount();
            wallet.post(payment.getDriverId(), payment.getBookingId(), paymentId,
                    "WALLET:" + (accepted ? "CAPTURE:" : "RELEASE:") + paymentId,
                    accepted ? "PAYMENT" : "RELEASE", amount, DRIVER_WALLET_HOLD,
                    accepted ? CUSTOMER_DEPOSIT_ESCROW : DRIVER_WALLET_PAYABLE,
                    accepted ? BigDecimal.ZERO : amount, amount.negate());
            payment.setStatus(accepted ? PaymentStatusEnum.SUCCESS : PaymentStatusEnum.FAILED);
            payments.save(payment);
            if (accepted) outbox.publishPaymentCompleted(payment);
            else outbox.publishPaymentFailed(payment, "Booking đã hết hạn hoặc không còn nhận thanh toán");
        });
    }

    @Scheduled(fixedDelayString = "${app.wallet-recovery-delay-ms:15000}", initialDelayString = "${app.wallet-recovery-delay-ms:15000}")
    public void recoverPending() {
        // Recovery must continue when new wallet payments are disabled.
        var pending = payments.findByGatewayAndStatusAndIdGreaterThanOrderByIdAsc(PaymentGatewayEnum.WALLET, PaymentStatusEnum.PENDING, recoveryCursor, PageRequest.of(0, 50));
        recoveryCursor = pending.isEmpty() ? 0 : pending.getLast().getId();
        for (var p : pending) {
            try { recover(p.getId()); }
            catch (RuntimeException failure) { log.error("Wallet recovery failed for payment {}", p.getId(), failure); }
        }
    }
}
