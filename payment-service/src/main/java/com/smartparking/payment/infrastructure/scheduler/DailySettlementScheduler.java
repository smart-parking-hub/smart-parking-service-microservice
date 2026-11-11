package com.smartparking.payment.infrastructure.scheduler;

import com.smartparking.payment.application.service.DailySettlementService;
import com.smartparking.payment.application.service.RefundService;
import com.smartparking.payment.application.service.impl.DailySettlementServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailySettlementScheduler {

    private final DailySettlementService dailySettlementService;

    @Autowired(required = false)
    private RefundService refundService;

    /**
     * Runs daily at 00:05 Asia/Ho_Chi_Minh to settle the prior business day [00:00, 24:00).
     * Idempotent across duplicate/concurrent runs and does not initiate bank payouts (D3).
     */
    @Scheduled(cron = "${app.settlement.cron:0 5 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void runPriorDaySettlement() {
        LocalDate priorDay = LocalDate.now(DailySettlementServiceImpl.BUSINESS_ZONE).minusDays(1);
        log.info("Starting scheduled daily settlement for business date {}", priorDay);
        dailySettlementService.runDailySettlement(priorDay);
    }

    /**
     * Periodically sweeps overdue refund requests where merchant failed to submit evidence by dueAt.
     * Transitions overdue REQUESTED refunds to REQUIRES_RECONCILIATION and publishes outbox events.
     */
    @Scheduled(cron = "${app.refund.reconcile.cron:0 */15 * * * *}")
    public void runOverdueRefundReconciliation() {
        if (refundService != null) {
            log.info("Running scheduled sweep for overdue manual refund requests");
            refundService.reconcileOverdueRefunds();
        }
    }
}

