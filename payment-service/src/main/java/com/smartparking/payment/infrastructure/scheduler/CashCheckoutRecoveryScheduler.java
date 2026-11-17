package com.smartparking.payment.infrastructure.scheduler;

import com.smartparking.payment.application.service.CashCheckoutService;
import com.smartparking.payment.domain.repository.CashCheckoutReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class CashCheckoutRecoveryScheduler {
    private final CashCheckoutReceiptRepository receipts;
    private final CashCheckoutService service;
    private long cursor;
    @Scheduled(fixedDelayString = "${app.cash-checkout-recovery-delay-ms:30000}", initialDelayString = "${app.cash-checkout-recovery-delay-ms:30000}")
    public void recover() {
        var pending = receipts.findByStatusAndBookingIdGreaterThanOrderByBookingIdAsc("PENDING", cursor, PageRequest.of(0, 20));
        cursor = pending.isEmpty() ? 0 : pending.getLast().getBookingId();
        for (var receipt : pending) {
            try { service.retry(receipt.getBookingId()); }
            catch (RuntimeException failure) { log.warn("Cash checkout {} is still awaiting confirmation", receipt.getBookingId()); }
        }
    }
}
