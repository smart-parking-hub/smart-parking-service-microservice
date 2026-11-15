package com.smartparking.payment;

import com.smartparking.common.enums.*;
import com.smartparking.common.exception.AppException;
import com.smartparking.payment.application.dto.CreatePaymentRequest;
import com.smartparking.payment.application.service.PaymentService;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment_recovery_it;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=10000")
class PaymentRecoveryIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("PAYMENT_RECOVERY_MYSQL_TEST_URL");
        if (url == null || url.isBlank()) return;
        if (!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-z0-9_]+_payos_recovery_it(?:\\?.*)?"))
            throw new IllegalArgumentException("Recovery integration test requires an isolated local *_payos_recovery_it database");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> System.getenv("PAYMENT_RECOVERY_MYSQL_TEST_PASSWORD"));
        registry.add("spring.sql.init.mode", () -> "always");
    }
    @Autowired PaymentService service;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockBean BookingPaymentVerifier verifier;
    @MockBean com.smartparking.payment.infrastructure.client.DisputeBookingClient disputeBookingClient;
    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) PayOS payOS;
    @MockBean KafkaTemplate<String, Object> kafka;
    private final AtomicInteger creates = new AtomicInteger();
    private final ConcurrentMap<Long, PaymentLink> links = new ConcurrentHashMap<>();
    private static final long BOOKING = 9_000_001L;

    @BeforeEach void setup() {
        payments.deleteAll();
        creates.set(0); links.clear();
        when(verifier.require(BOOKING, 7L)).thenReturn(new BookingPaymentVerifier.Quote(BOOKING, 7L, 9L, 3L,
                "PENDING_PAYMENT", new BigDecimal("4000"), new BigDecimal("20000"), LocalDateTime.now().plusMinutes(10)));
        when(payOS.paymentRequests().get(anyLong())).thenAnswer(inv -> links.get(inv.getArgument(0)));
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenAnswer(inv -> {
            CreatePaymentLinkRequest request = inv.getArgument(0);
            creates.incrementAndGet();
            long code = request.getOrderCode();
            var link = mock(PaymentLink.class);
            when(link.getOrderCode()).thenReturn(code); when(link.getAmount()).thenReturn(4000L);
            when(link.getAmountPaid()).thenReturn(0L); when(link.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
            links.put(code, link);
            var response = mock(CreatePaymentLinkResponse.class);
            when(response.getOrderCode()).thenReturn(code); when(response.getAmount()).thenReturn(4000L);
            when(response.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/order-" + code);
            return response;
        });
    }
    private CreatePaymentRequest request() { return CreatePaymentRequest.builder().bookingId(BOOKING).paymentType("DEPOSIT").build(); }
    private List<String> simultaneousRequests() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return service.createPaymentLink(7L, request()); };
            var a = pool.submit(task); var b = pool.submit(task); start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    @Test void simultaneousFirstCreateUsesOneRealDatabaseLockAndOneOrder() throws Exception {
        List<String> urls = simultaneousRequests();
        assertEquals(urls.getFirst(), urls.getLast());
        assertEquals(1, creates.get());
        assertEquals(1, payments.findByBookingId(BOOKING).size());
    }

    @Test void simultaneousRetryKeepsCancelledHistoryAndCreatesOnlyOneFreshOrder() throws Exception {
        service.createPaymentLink(7L, request());
        var old = payments.findByBookingId(BOOKING).getFirst();
        when(links.get(old.getOrderCode()).getStatus()).thenReturn(PaymentLinkStatus.CANCELLED);
        List<String> urls = simultaneousRequests();
        assertEquals(urls.getFirst(), urls.getLast()); assertEquals(2, creates.get());
        var rows = payments.findByBookingId(BOOKING);
        assertEquals(2, rows.size());
        assertEquals(PaymentStatusEnum.FAILED, payments.findById(old.getId()).orElseThrow().getStatus());
        assertEquals(2, rows.stream().map(Payment::getOrderCode).distinct().count());
        assertEquals(1, rows.stream().filter(p -> p.getStatus() == PaymentStatusEnum.PENDING).count());
    }

    @Test void providerTimeoutRetainsReservedOrderAndBlocksAnotherCharge() {
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenThrow(new RuntimeException("timeout after send"));
        assertThrows(AppException.class, () -> service.createPaymentLink(7L, request()));
        var reserved = payments.findByBookingId(BOOKING);
        assertEquals(1, reserved.size()); assertNull(reserved.getFirst().getCheckoutUrl());
        assertThrows(AppException.class, () -> service.createPaymentLink(7L, request()));
        verify(payOS.paymentRequests(), times(1)).create(any(CreatePaymentLinkRequest.class));
        assertEquals(1, payments.findByBookingId(BOOKING).size());
    }

    @Test void orderCodesAreUniqueAcrossBookingsAndTransactionRollbacks() {
        var template = new TransactionTemplate(transactions);
        Long lost = template.execute(status -> {
            var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(c -> c.prepareStatement("INSERT INTO payment_order_codes () VALUES ()", java.sql.Statement.RETURN_GENERATED_KEYS), keys);
            status.setRollbackOnly(); return keys.getKey().longValue();
        });
        service.createPaymentLink(7L, request());
        assertTrue(payments.findByBookingId(BOOKING).getFirst().getOrderCode() > lost);
    }
}
