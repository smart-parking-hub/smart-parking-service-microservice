package com.smartparking.payment.domain.model;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "ledger_journals")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_key", nullable = false, unique = true, updatable = false, length = 150)
    private String journalKey;

    @Column(name = "journal_type", nullable = false, updatable = false, length = 60)
    private String journalType;

    @Column(name = "merchant_id", updatable = false)
    private Long merchantId;

    @Column(name = "driver_id", updatable = false)
    private Long driverId;

    @Column(name = "booking_id", updatable = false)
    private Long bookingId;

    @Column(name = "payment_id", updatable = false)
    private Long paymentId;

    @Column(name = "settlement_date", updatable = false)
    private LocalDate settlementDate;

    @Column(nullable = false, updatable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "total_debit", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal totalCredit;

    @Column(name = "fee_percent", nullable = false, updatable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal feePercent = BigDecimal.ZERO;

    @Column(name = "reversal_of_journal_id", updatable = false)
    private Long reversalOfJournalId;

    @Column(updatable = false, length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.EAGER)
    @Builder.Default
    private List<LedgerPosting> postings = new ArrayList<>();

    public List<LedgerPosting> getPostings() {
        return Collections.unmodifiableList(postings);
    }

    public void addPosting(LedgerAccountCode accountCode, Long merchantId, PostingDirection direction, BigDecimal amount) {
        if (accountCode == null || direction == null || amount == null || amount.signum() <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ledger posting requires positive amount, account and direction");
        }
        LedgerPosting posting = LedgerPosting.builder()
                .journal(this)
                .accountCode(accountCode)
                .merchantId(merchantId)
                .direction(direction)
                .amount(amount)
                .currency(this.currency != null ? this.currency : "VND")
                .createdAt(this.createdAt != null ? this.createdAt : LocalDateTime.now())
                .build();
        this.postings.add(posting);
    }

    public void addDriverPosting(LedgerAccountCode account, Long owner, PostingDirection direction, BigDecimal amount) {
        addPosting(account, null, direction, amount);
        postings.removeLast();
        postings.add(LedgerPosting.builder().journal(this).accountCode(account).driverId(owner)
                .direction(direction).amount(amount).currency(currency).build());
    }

    public void validateAndSeal() {
        if (journalKey == null || journalKey.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ledger journal key is required");
        }
        if (postings == null || postings.size() < 2) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Double-entry journal requires at least one debit and one credit posting");
        }
        BigDecimal debitSum = BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;
        for (LedgerPosting posting : postings) {
            if (posting.getAmount() == null || posting.getAmount().signum() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Posting amount must be strictly positive");
            }
            if (posting.getDirection() == PostingDirection.DEBIT) {
                debitSum = debitSum.add(posting.getAmount());
            } else if (posting.getDirection() == PostingDirection.CREDIT) {
                creditSum = creditSum.add(posting.getAmount());
            } else {
                throw new AppException(ErrorCode.BAD_REQUEST, "Invalid posting direction");
            }
        }
        if (debitSum.signum() <= 0 || debitSum.compareTo(creditSum) != 0) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Unbalanced ledger journal " + journalKey + ": debit=" + debitSum + " credit=" + creditSum);
        }
        this.totalDebit = debitSum;
        this.totalCredit = creditSum;
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public BigDecimal netMerchantPayableDelta(Long targetMerchantId) {
        validateAndSeal();
        BigDecimal delta = BigDecimal.ZERO;
        for (LedgerPosting posting : postings) {
            if (posting.getAccountCode() == LedgerAccountCode.MERCHANT_PAYABLE
                    && (targetMerchantId == null || targetMerchantId.equals(posting.getMerchantId()))) {
                if (posting.getDirection() == PostingDirection.CREDIT) {
                    delta = delta.add(posting.getAmount());
                } else {
                    delta = delta.subtract(posting.getAmount());
                }
            }
        }
        return delta;
    }

    @PrePersist
    protected void onCreate() {
        validateAndSeal();
    }

    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Ledger journals are append-only; use a reversal journal instead of updating");
    }
}

