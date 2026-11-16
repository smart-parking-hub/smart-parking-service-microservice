package com.smartparking.payment.infrastructure.scheduler;
import com.smartparking.payment.application.service.impl.RefundServiceImpl;
import com.smartparking.payment.domain.repository.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A durable refund request survives a cancellation racing the booking confirmation/capture. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletRefundRecoveryScheduler {
    private final RefundRequestRepository refunds;
    private final RefundServiceImpl service;
    private long cursor;
    @Scheduled(fixedDelayString = "${app.wallet-recovery-delay-ms:15000}", initialDelayString = "${app.wallet-recovery-delay-ms:15000}")
    public void recover() {
        var pending = refunds.findByRefundChannelAndStatusAndIdGreaterThanOrderByIdAsc("WALLET", "REQUESTED", cursor, PageRequest.of(0, 50));
        cursor = pending.isEmpty() ? 0 : pending.getLast().getId();
        for (var refund : pending) {
            try { service.retryWalletRefund(refund.getId(), null); }
            catch (com.smartparking.common.exception.AppException expected) {
                log.debug("Wallet refund {} awaits payment/funding reconciliation", refund.getId());
            } catch (RuntimeException failure) { log.error("Wallet refund recovery failed for {}", refund.getId(), failure); }
        }
    }
}
