package com.smartparking.payment.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.domain.model.*;
import com.smartparking.payment.domain.repository.LedgerJournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.smartparking.payment.domain.model.LedgerAccountCode.*;
import static com.smartparking.payment.domain.model.PostingDirection.*;

@Service
@RequiredArgsConstructor
public class DriverWalletLedger {
    private final JdbcTemplate jdbc;
    private final LedgerJournalRepository journals;

    public record Balance(BigDecimal availableAmount, BigDecimal heldAmount, String currency, boolean enabled) {}
    public record Entry(Long id, Long bookingId, Long paymentId, String entryType,
                        BigDecimal amount, BigDecimal heldDelta, LocalDateTime createdAt) {}

    @Transactional(readOnly = true)
    public Balance balance(Long driverId, boolean enabled) {
        return jdbc.query("SELECT available_amount, held_amount FROM driver_wallets WHERE driver_id=?",
                (r, n) -> new Balance(r.getBigDecimal(1), r.getBigDecimal(2), "VND", enabled), driverId)
                .stream().findFirst().orElse(new Balance(BigDecimal.ZERO, BigDecimal.ZERO, "VND", enabled));
    }

    @Transactional(readOnly = true)
    public PageResponse<Entry> entries(Long driverId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new AppException(ErrorCode.BAD_REQUEST, "Phân trang không hợp lệ");
        var rows = jdbc.query("SELECT * FROM driver_wallet_entries WHERE driver_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
                (r, n) -> new Entry(r.getLong("id"), r.getObject("booking_id", Long.class),
                        r.getObject("payment_id", Long.class), r.getString("entry_type"), r.getBigDecimal("amount"),
                        r.getBigDecimal("held_delta"), r.getTimestamp("created_at").toLocalDateTime()), driverId, size, (long) page * size);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM driver_wallet_entries WHERE driver_id=?", Long.class, driverId);
        return new PageResponse<>(new PageImpl<>(rows, PageRequest.of(page, size), count == null ? 0 : count));
    }

    /** Caller holds the payment-attempt lock before entering the driver's row lock. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void post(Long driverId, Long bookingId, Long paymentId, String key, String type,
                     BigDecimal amount, LedgerAccountCode debit, LedgerAccountCode credit,
                     BigDecimal availableDelta, BigDecimal heldDelta) {
        if (driverId == null || amount == null || amount.signum() <= 0) throw new AppException(ErrorCode.BAD_REQUEST, "Bút toán ví không hợp lệ");
        jdbc.update("INSERT INTO driver_wallets(driver_id) VALUES (?) ON DUPLICATE KEY UPDATE driver_id=driver_id", driverId);
        jdbc.queryForObject("SELECT driver_id FROM driver_wallets WHERE driver_id=? FOR UPDATE", Long.class, driverId);
        if (journals.existsByJournalKey(key)) return;
        int updated = jdbc.update("UPDATE driver_wallets SET available_amount=available_amount+?, held_amount=held_amount+? "
                + "WHERE driver_id=? AND available_amount+? >= 0 AND held_amount+? >= 0",
                availableDelta, heldDelta, driverId, availableDelta, heldDelta);
        if (updated != 1) throw new AppException(ErrorCode.BAD_REQUEST, "Số dư ví khả dụng không đủ");
        Long merchantId = paymentId == null ? null : jdbc.queryForObject("SELECT merchant_id FROM payments WHERE id=?", Long.class, paymentId);
        var journal = LedgerJournal.builder().merchantId(merchantId).journalKey(key).journalType(type).driverId(driverId)
                .bookingId(bookingId).paymentId(paymentId).currency("VND").description(type + " booking #" + bookingId).build();
        if (debit == DRIVER_WALLET_PAYABLE || debit == DRIVER_WALLET_HOLD) journal.addDriverPosting(debit, driverId, DEBIT, amount);
        else journal.addPosting(debit, merchantId, DEBIT, amount);
        if (credit == DRIVER_WALLET_PAYABLE || credit == DRIVER_WALLET_HOLD) journal.addDriverPosting(credit, driverId, CREDIT, amount);
        else journal.addPosting(credit, merchantId, CREDIT, amount);
        journal.validateAndSeal();
        journals.saveAndFlush(journal);
        jdbc.update("INSERT INTO driver_wallet_entries(driver_id,journal_id,payment_id,booking_id,entry_type,amount,held_delta,created_at) VALUES(?,?,?,?,?,?,?,?)",
                driverId, journal.getId(), paymentId, bookingId, type, availableDelta, heldDelta,
                LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
    }
}
