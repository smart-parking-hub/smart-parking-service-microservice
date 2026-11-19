package com.smartparking.payment.infrastructure.client;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
@Component
@Slf4j
public class BookingPaymentVerifier {
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory<?, ?> breakers;
    @Value("${app.internal-service-token:}") private String serviceToken;
    public BookingPaymentVerifier(@Value("${services.booking.url:${BOOKING_SERVICE_URL:http://localhost:8083}}") String url) {
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public record Quote(Long id, Long driverId, Long merchantId, Long parkingLotId, String status,
                        BigDecimal advanceDeposit, BigDecimal totalAmount, LocalDateTime expiresAt) {
        public BigDecimal payable(PaymentTypeEnum type) {
            if (type == PaymentTypeEnum.DEPOSIT && "PENDING_PAYMENT".equals(status) && expiresAt != null && expiresAt.isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) return advanceDeposit;
            if (type == PaymentTypeEnum.FINAL_PAYMENT && "AWAITING_FINAL_PAYMENT".equals(status)) {
                BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
                BigDecimal deposit = advanceDeposit != null ? advanceDeposit : BigDecimal.ZERO;
                return total.subtract(deposit).max(BigDecimal.ZERO);
            }
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không còn ở trạng thái được thanh toán");
        }
    }
    public Quote require(Long bookingId, Long userId) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String token = attributes instanceof ServletRequestAttributes context ? context.getRequest().getHeader("Authorization") : null;
        if (token == null || !token.startsWith("Bearer ")) throw new AppException(ErrorCode.FORBIDDEN, "Thiếu phiên xác thực booking");
        Quote quote = fetchQuote("/api/v1/bookings/{id}", bookingId, token);
        if (quote == null || !bookingId.equals(quote.id()) || !userId.equals(quote.driverId()) || quote.merchantId() == null)
            throw new AppException(ErrorCode.FORBIDDEN, "Không xác minh được quyền thanh toán booking");
        return quote;
    }
    public Quote requireMerchantFinalPayment(Long bookingId, Long merchantId) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String token = attributes instanceof ServletRequestAttributes context ? context.getRequest().getHeader("Authorization") : null;
        if (token == null || !token.startsWith("Bearer ")) throw new AppException(ErrorCode.FORBIDDEN, "Thiếu phiên xác thực booking");
        Quote quote = fetchQuote("/api/v1/merchant/bookings/{id}", bookingId, token);
        if (quote == null || !bookingId.equals(quote.id()) || !merchantId.equals(quote.merchantId()))
            throw new AppException(ErrorCode.FORBIDDEN, "Lượt đỗ không thuộc bãi của bạn");
        return quote;
    }

    private Quote fetchQuote(String path, Long bookingId, String token) {
        try {
            var response = client.get().uri(path, bookingId).header("Authorization", token)
                    .retrieve().body(new ParameterizedTypeReference<ApiResponse<Quote>>() {});
            if (response == null || response.getData() == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Chưa xác minh được thông tin đặt chỗ. Vui lòng thử lại sau.");
            }
            return response.getData();
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            log.warn("Booking verification failed for booking {} with HTTP {}", bookingId, status);
            switch (status) {
                case 401 -> throw new AppException(ErrorCode.UNAUTHORIZED, "Phiên xác thực đặt chỗ không hợp lệ. Vui lòng đăng nhập lại.");
                case 403 -> throw new AppException(ErrorCode.FORBIDDEN, "Không xác minh được quyền thanh toán đặt chỗ.");
                case 404 -> throw new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy lượt đặt chỗ.");
                default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Chưa xác minh được thông tin đặt chỗ. Vui lòng thử lại sau.");
            }
        } catch (RestClientException failure) {
            log.warn("Booking verification unavailable for booking {}", bookingId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa kết nối được dịch vụ đặt chỗ. Vui lòng thử lại sau.");
        }
    }

    public boolean confirm(com.smartparking.payment.domain.model.Payment payment) {
        if (serviceToken == null || serviceToken.isBlank()) throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Chưa cấu hình xác nhận thanh toán nội bộ");
        var result = client.post().uri("/api/v1/internal/bookings/{id}/confirm-payment", payment.getBookingId())
                .header("X-Internal-Service-Token", serviceToken)
                .body(java.util.Map.of("driverId", payment.getDriverId(), "merchantId", payment.getMerchantId(),
                        "amount", payment.getAmount(), "paymentType", payment.getPaymentType().name(), "paymentId", payment.getId()))
                .retrieve().body(new ParameterizedTypeReference<ApiResponse<Boolean>>() {});
        return result != null && Boolean.TRUE.equals(result.getData());
    }

    public boolean confirmCash(Long bookingId, com.smartparking.common.dto.CashCheckoutCommand command) {
        if (serviceToken == null || serviceToken.isBlank()) throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Thiếu cấu hình xác nhận nội bộ");
        return breakers.create("cash-booking-confirm").run(() -> {
            var result = client.post().uri("/api/v1/internal/bookings/{id}/confirm-cash-checkout", bookingId)
                    .header("X-Internal-Service-Token", serviceToken).body(command).retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Boolean>>() {});
            if (result == null || result.getData() == null) throw new IllegalStateException("Chưa xác định kết quả thu tiền mặt");
            return result.getData();
        });
    }

    public boolean confirmWallet(com.smartparking.payment.domain.model.Payment payment) {
        return breakers.create("wallet-booking-confirm").run(() -> confirmWalletRequest(payment));
    }
    private boolean confirmWalletRequest(com.smartparking.payment.domain.model.Payment payment) {
        if (serviceToken == null || serviceToken.isBlank()) throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Thiếu cấu hình xác nhận nội bộ");
        var result = client.post().uri("/api/v1/internal/bookings/{id}/confirm-wallet-payment", payment.getBookingId())
                .header("X-Internal-Service-Token", serviceToken)
                .body(java.util.Map.of("paymentId", payment.getId(), "driverId", payment.getDriverId(),
                        "merchantId", payment.getMerchantId(), "amount", payment.getAmount()))
                .retrieve().body(new ParameterizedTypeReference<ApiResponse<Boolean>>() {});
        if (result == null || result.getData() == null) throw new IllegalStateException("Chưa xác định kết quả xác nhận ví");
        return result.getData();
    }

    public record HoldState(Long bookingId, Long driverId, String status, boolean unpaidReleaseCandidate) {}
    public HoldState holdState(Long bookingId) {
        if (serviceToken==null || serviceToken.isBlank()) throw new AppException(ErrorCode.FORBIDDEN,"Thiếu xác thực nội bộ");
        var response=client.get().uri("/api/v1/internal/bookings/{id}/unpaid-hold-state",bookingId)
                .header("X-Internal-Service-Token",serviceToken).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<HoldState>>() {});
        return response==null ? null : response.getData();
    }

}
