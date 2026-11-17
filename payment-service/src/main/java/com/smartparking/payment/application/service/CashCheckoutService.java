package com.smartparking.payment.application.service;

import com.smartparking.common.dto.CashCheckoutCommand;
import com.smartparking.common.enums.*;
import com.smartparking.common.exception.*;
import com.smartparking.payment.domain.model.CashCheckoutReceipt;
import com.smartparking.payment.domain.repository.*;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.Objects;

@Service
public class CashCheckoutService {
    private final CashCheckoutReceiptRepository receipts;
    private final PaymentRepository payments;
    private final PaymentAttemptGuard guard;
    private final PayOS payOS;
    private final BookingPaymentVerifier booking;
    private final TransactionTemplate tx;

    public CashCheckoutService(CashCheckoutReceiptRepository receipts, PaymentRepository payments,
            PaymentAttemptGuard guard, PayOS payOS, BookingPaymentVerifier booking, PlatformTransactionManager manager) {
        this.receipts = receipts; this.payments = payments; this.guard = guard;
        this.payOS = payOS; this.booking = booking;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public boolean confirm(Long bookingId, CashCheckoutCommand command) {
        if (bookingId == null || command.merchantId() == null || command.parkingLotId() == null
                || command.amount() == null || command.amount().signum() <= 0)
            throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu thông tin xác nhận tiền mặt");
        CashCheckoutReceipt receipt = tx.execute(status -> reserve(bookingId, command));
        if ("COMPLETED".equals(receipt.getStatus())) return true;
        return complete(receipt);
    }

    public boolean retry(Long bookingId) {
        CashCheckoutReceipt receipt = tx.execute(status -> {
            guard.lock(bookingId, PaymentTypeEnum.FINAL_PAYMENT);
            return receipts.findById(bookingId).orElseThrow();
        });
        return "COMPLETED".equals(receipt.getStatus()) || ("PENDING".equals(receipt.getStatus()) && complete(receipt));
    }

    private CashCheckoutReceipt reserve(Long bookingId, CashCheckoutCommand command) {
        guard.lock(bookingId, PaymentTypeEnum.FINAL_PAYMENT);
        var existing = receipts.findById(bookingId);
        if (existing.isPresent()) {
            var receipt = existing.get();
            if (!Objects.equals(receipt.getMerchantId(), command.merchantId())
                    || !Objects.equals(receipt.getParkingLotId(), command.parkingLotId())
                    || receipt.getAmount().compareTo(command.amount()) != 0)
                throw new AppException(ErrorCode.CONFLICT, "Yêu cầu tiền mặt không khớp kết quả đã lưu");
            if (!"REJECTED".equals(receipt.getStatus())) return receipt;
        }
        for (var attempt : payments.findAttemptsForUpdate(bookingId, PaymentTypeEnum.FINAL_PAYMENT)) {
            if (attempt.getPaymentType() != PaymentTypeEnum.FINAL_PAYMENT) continue;
            if (attempt.getStatus() == PaymentStatusEnum.SUCCESS || attempt.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW
                    || attempt.getStatus() == PaymentStatusEnum.REFUNDED || attempt.getGateway() != PaymentGatewayEnum.PAYOS)
                throw conflict();
            try {
                var link = payOS.paymentRequests().get(attempt.getOrderCode());
                if (link == null || !Objects.equals(link.getOrderCode(), attempt.getOrderCode())
                        || attempt.getAmount() == null || link.getAmount() == null
                        || attempt.getAmount().compareTo(BigDecimal.valueOf(link.getAmount())) != 0
                        || link.getStatus() != PaymentLinkStatus.CANCELLED || link.getAmountPaid() == null
                        || link.getAmountPaid() != 0) throw conflict();
            } catch (AppException failure) { throw failure; }
            catch (RuntimeException failure) { throw conflict(); }
        }
        var receipt = CashCheckoutReceipt.builder().bookingId(bookingId).merchantId(command.merchantId())
                .parkingLotId(command.parkingLotId()).amount(command.amount()).status("PENDING").updatedAt(now()).build();
        // Committed BEFORE the booking RPC. A timeout must never reopen PayOS collection.
        return receipts.saveAndFlush(receipt);
    }

    private boolean complete(CashCheckoutReceipt receipt) {
        boolean accepted = booking.confirmCash(receipt.getBookingId(),
                new CashCheckoutCommand(receipt.getMerchantId(), receipt.getParkingLotId(), receipt.getAmount()));
        tx.executeWithoutResult(status -> {
            guard.lock(receipt.getBookingId(), PaymentTypeEnum.FINAL_PAYMENT);
            var current = receipts.findById(receipt.getBookingId()).orElseThrow();
            if ("PENDING".equals(current.getStatus())) {
                current.setStatus(accepted ? "COMPLETED" : "REJECTED"); current.setUpdatedAt(now()); receipts.save(current);
            }
        });
        return accepted;
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")); }
    private static AppException conflict() {
        return new AppException(ErrorCode.CONFLICT, "Phiên PayOS chưa hủy hoặc đã nhận tiền; chưa được xác nhận tiền mặt");
    }
}
