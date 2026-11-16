package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.*;
import com.smartparking.common.exception.*;
import com.smartparking.payment.domain.model.*;
import com.smartparking.payment.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepositStatementService {
    private final PaymentRepository payments;
    private final LedgerJournalRepository journals;
    private final RefundRequestRepository refunds;
    public record Statement(Long paymentId, Long bookingId, Long refundId, String paymentStatus, String gateway,
            BigDecimal depositCollected, BigDecimal refundAmount, BigDecimal refundCompleted, BigDecimal refundOutstanding,
            String refundStatus, String refundChannel, BigDecimal escrowRemaining,
            BigDecimal depositPlatformFee, BigDecimal depositMerchantNet, boolean reconciled) {}

    @Transactional(readOnly = true)
    public PageResponse<Statement> read(Long merchantId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new AppException(ErrorCode.BAD_REQUEST, "Phân trang không hợp lệ");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var rows = merchantId == null ? payments.findByPaymentType(PaymentTypeEnum.DEPOSIT, pageable)
                : payments.findByMerchantIdAndPaymentType(merchantId, PaymentTypeEnum.DEPOSIT, pageable);
        var ids = rows.getContent().stream().map(Payment::getId).toList();
        if (ids.isEmpty()) return new PageResponse<>(rows.map(p -> (Statement) null));
        var byPayment = journals.findByPaymentIdIn(ids).stream().collect(Collectors.groupingBy(LedgerJournal::getPaymentId));
        var refundByPayment = refunds.findByPaymentIdIn(ids).stream().collect(Collectors.toMap(RefundRequest::getPaymentId, r -> r));
        return new PageResponse<>(rows.map(p -> statement(p, byPayment.getOrDefault(p.getId(), List.of()), refundByPayment.get(p.getId()))));
    }

    private Statement statement(Payment p, List<LedgerJournal> entries, RefundRequest refund) {
        var postings = entries.stream().flatMap(j -> j.getPostings().stream()).toList();
        BigDecimal fee = net(postings, LedgerAccountCode.PLATFORM_FEE_REVENUE);
        BigDecimal merchant = net(postings, LedgerAccountCode.MERCHANT_PAYABLE);
        BigDecimal escrow = net(postings, LedgerAccountCode.CUSTOMER_DEPOSIT_ESCROW);
        BigDecimal pending = net(postings, LedgerAccountCode.REFUND_PAYABLE);
        BigDecimal completed = entries.stream().filter(j -> "REFUND_COMPLETED".equals(j.getJournalType()) || "REFUND".equals(j.getJournalType()))
                .flatMap(j -> j.getPostings().stream()).filter(x -> x.getAccountCode() == LedgerAccountCode.REFUND_PAYABLE && x.getDirection() == PostingDirection.DEBIT)
                .map(LedgerPosting::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean collected = p.getStatus() == PaymentStatusEnum.SUCCESS || p.getStatus() == PaymentStatusEnum.REFUNDED;
        BigDecimal deposit = collected ? p.getAmount() : BigDecimal.ZERO;
        return new Statement(p.getId(), p.getBookingId(), refund == null ? null : refund.getId(), p.getStatus().name(), p.getGateway().name(), deposit,
                refund == null ? BigDecimal.ZERO : refund.getRefundAmount(), completed, pending,
                refund == null ? "NONE" : refund.getStatus(), refund == null ? null : refund.getRefundChannel(), escrow, fee, merchant,
                deposit.compareTo(completed.add(pending).add(escrow).add(fee).add(merchant)) == 0);
    }
    private BigDecimal net(List<LedgerPosting> entries, LedgerAccountCode account) {
        return entries.stream().filter(p -> p.getAccountCode() == account)
                .map(p -> p.getDirection() == PostingDirection.CREDIT ? p.getAmount() : p.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
