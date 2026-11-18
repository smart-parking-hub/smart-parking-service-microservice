package com.smartparking.payment;

import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.payment.domain.model.CashCheckoutReceipt;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.CashCheckoutReceiptRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:channelstats;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MerchantRevenueChannelRepositoryTest {

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    CashCheckoutReceiptRepository cashReceipts;

    @BeforeEach
    void setUp() {
        cashReceipts.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void aggregatesSuccessPaymentsByLotAndChannelAndOnlyCompletedCashReceipts() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);

        Payment p1 = Payment.builder()
                .bookingId(1L).driverId(2L).merchantId(10L).parkingLotId(101L)
                .amount(new BigDecimal("100000")).gateway(PaymentGatewayEnum.PAYOS)
                .paymentType(PaymentTypeEnum.DEPOSIT).paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS).transactionCode("TXN-1").orderCode(1001L).idempotencyKey("K1")
                .build();
        p1.setCreatedAt(now);

        Payment p2 = Payment.builder()
                .bookingId(2L).driverId(2L).merchantId(10L).parkingLotId(101L)
                .amount(new BigDecimal("60000")).gateway(PaymentGatewayEnum.PAYOS)
                .paymentType(PaymentTypeEnum.FINAL_PAYMENT).paymentChannel("COUNTER_QR")
                .status(PaymentStatusEnum.SUCCESS).transactionCode("TXN-2").orderCode(1002L).idempotencyKey("K2")
                .build();
        p2.setCreatedAt(now);

        Payment pFailed = Payment.builder()
                .bookingId(3L).driverId(2L).merchantId(10L).parkingLotId(101L)
                .amount(new BigDecimal("999000")).gateway(PaymentGatewayEnum.PAYOS)
                .paymentType(PaymentTypeEnum.FINAL_PAYMENT).paymentChannel("COUNTER_QR")
                .status(PaymentStatusEnum.FAILED).transactionCode("TXN-3").orderCode(1003L).idempotencyKey("K3")
                .build();
        pFailed.setCreatedAt(now);

        paymentRepository.saveAndFlush(p1);
        paymentRepository.saveAndFlush(p2);
        paymentRepository.saveAndFlush(pFailed);

        cashReceipts.saveAndFlush(CashCheckoutReceipt.builder()
                .bookingId(11L).merchantId(10L).parkingLotId(101L)
                .amount(new BigDecimal("45000")).status("COMPLETED").updatedAt(now).build());
        cashReceipts.saveAndFlush(CashCheckoutReceipt.builder()
                .bookingId(12L).merchantId(10L).parkingLotId(101L)
                .amount(new BigDecimal("50000")).status("PENDING").updatedAt(now).build());
        cashReceipts.saveAndFlush(CashCheckoutReceipt.builder()
                .bookingId(13L).merchantId(10L).parkingLotId(102L)
                .amount(new BigDecimal("75000")).status("REJECTED").updatedAt(now).build());

        var channelAggs = paymentRepository.aggregateSuccessByMerchantGroupedByLotAndChannel(10L);
        assertEquals(2, channelAggs.size());

        var online = channelAggs.stream().filter(a -> "ONLINE_TRANSFER".equals(a.getPaymentChannel())).findFirst().orElseThrow();
        var counterQr = channelAggs.stream().filter(a -> "COUNTER_QR".equals(a.getPaymentChannel())).findFirst().orElseThrow();

        assertEquals(0, new BigDecimal("100000").compareTo(online.getTotalAmount()));
        assertEquals(1L, online.getPaymentCount());
        assertEquals(0, new BigDecimal("60000").compareTo(counterQr.getTotalAmount()));
        assertEquals(1L, counterQr.getPaymentCount());

        var cashAggs = cashReceipts.aggregateCompletedByMerchantGroupedByLot(10L);
        assertEquals(1, cashAggs.size());
        assertEquals(101L, cashAggs.getFirst().getParkingLotId());
        assertEquals(0, new BigDecimal("45000").compareTo(cashAggs.getFirst().getTotalAmount()));
        assertEquals(1L, cashAggs.getFirst().getBookingCount());
    }
}
