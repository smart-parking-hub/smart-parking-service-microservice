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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Component
public class BookingPaymentVerifier {
    private final RestClient client;
    @Value("${app.internal-service-token:}") private String serviceToken;
    public BookingPaymentVerifier(@Value("${services.booking.url:${BOOKING_SERVICE_URL:http://localhost:8083}}") String url) {
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public record Quote(Long id, Long driverId, Long merchantId, Long parkingLotId, String status,
                        BigDecimal advanceDeposit, BigDecimal totalAmount, LocalDateTime expiresAt) {
        public BigDecimal payable(PaymentTypeEnum type) {
            if (type == PaymentTypeEnum.DEPOSIT && "PENDING_PAYMENT".equals(status) && expiresAt != null && expiresAt.isAfter(LocalDateTime.now())) return advanceDeposit;
            if (type == PaymentTypeEnum.FINAL_PAYMENT && "AWAITING_FINAL_PAYMENT".equals(status)) return totalAmount.subtract(advanceDeposit).max(BigDecimal.ZERO);
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không còn ở trạng thái được thanh toán");
        }
    }
    public Quote require(Long bookingId, Long userId) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String token = attributes instanceof ServletRequestAttributes context ? context.getRequest().getHeader("Authorization") : null;
        if (token == null || !token.startsWith("Bearer ")) throw new AppException(ErrorCode.FORBIDDEN, "Thiếu phiên xác thực booking");
        var result = client.get().uri("/api/v1/bookings/{id}", bookingId).header("Authorization", token)
                .retrieve().body(new ParameterizedTypeReference<ApiResponse<Quote>>() {});
        Quote quote = result == null ? null : result.getData();
        if (quote == null || !userId.equals(quote.driverId()) || quote.merchantId() == null)
            throw new AppException(ErrorCode.FORBIDDEN, "Không xác minh được quyền thanh toán booking");
        return quote;
    }
    public boolean confirm(com.smartparking.payment.domain.model.Payment payment) {
        if (serviceToken == null || serviceToken.isBlank()) throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Chưa cấu hình xác nhận thanh toán nội bộ");
        var result = client.post().uri("/api/v1/internal/bookings/{id}/confirm-payment", payment.getBookingId())
                .header("X-Internal-Service-Token", serviceToken)
                .body(java.util.Map.of("driverId", payment.getDriverId(), "merchantId", payment.getMerchantId(),
                        "amount", payment.getAmount(), "paymentType", payment.getPaymentType().name()))
                .retrieve().body(new ParameterizedTypeReference<ApiResponse<Boolean>>() {});
        return result != null && Boolean.TRUE.equals(result.getData());
    }

}
