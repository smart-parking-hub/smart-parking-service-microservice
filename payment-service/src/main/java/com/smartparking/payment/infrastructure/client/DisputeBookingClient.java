package com.smartparking.payment.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.security.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Slf4j
public class DisputeBookingClient {

    private static final String BREAKER_ID = "dispute-booking";

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Autowired
    public DisputeBookingClient(
            @Value("${services.booking.url:${BOOKING_SERVICE_URL:http://localhost:8083}}") String bookingServiceUrl,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this(createDefaultBuilder(bookingServiceUrl), circuitBreakerFactory);
    }

    public DisputeBookingClient(RestClient.Builder builder, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.build();
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    private static RestClient.Builder createDefaultBuilder(String bookingServiceUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder()
                .baseUrl(bookingServiceUrl)
                .requestFactory(factory);
    }

    public record VerifiedBooking(Long bookingId, Long driverId, Long merchantId, Long parkingLotId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BookingLookupPayload(Long id, Long driverId, Long merchantId, Long parkingLotId) {
    }

    private sealed interface LookupOutcome permits LookupSuccess, LookupNotFound, LookupForbidden, LookupUnauthorized {
    }

    private record LookupSuccess(VerifiedBooking booking) implements LookupOutcome {
    }

    private record LookupNotFound() implements LookupOutcome {
    }

    private record LookupForbidden() implements LookupOutcome {
    }

    private record LookupUnauthorized() implements LookupOutcome {
    }

    private static final class InvalidBookingPayloadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidBookingPayloadException() {
            super("Invalid booking verification payload");
        }
    }

    public VerifiedBooking verifyBookingOwnership(Long bookingId, Long driverUserId) {
        if (bookingId == null || driverUserId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu thông tin xác minh lượt đặt chỗ");
        }
        String bearerToken = extractBearerToken();

        LookupOutcome outcome = circuitBreakerFactory.create(BREAKER_ID).run(() -> {
            try {
                ApiResponse<BookingLookupPayload> response = restClient.get()
                        .uri("/api/v1/bookings/{id}", bookingId)
                        .header(SecurityConstants.AUTH_HEADER, bearerToken)
                        .retrieve()
                        .body(new ParameterizedTypeReference<ApiResponse<BookingLookupPayload>>() {});
                BookingLookupPayload payload = response == null ? null : response.getData();
                if (payload == null
                        || !bookingId.equals(payload.id())
                        || payload.driverId() == null
                        || payload.merchantId() == null
                        || payload.parkingLotId() == null) {
                    log.warn("Booking verification returned invalid payload for bookingId={}", bookingId);
                    throw new InvalidBookingPayloadException();
                }
                if (!driverUserId.equals(payload.driverId())) {
                    return new LookupForbidden();
                }
                return new LookupSuccess(new VerifiedBooking(
                        payload.id(),
                        payload.driverId(),
                        payload.merchantId(),
                        payload.parkingLotId()
                ));
            } catch (HttpClientErrorException.NotFound ex) {
                return new LookupNotFound();
            } catch (HttpClientErrorException.Forbidden ex) {
                return new LookupForbidden();
            } catch (HttpClientErrorException.Unauthorized ex) {
                return new LookupUnauthorized();
            }
        }, throwable -> {
            log.error("Failed to verify booking {} with booking-service: {}", bookingId, throwable.getMessage());
            throw new AppException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Không thể xác minh thông tin lượt đặt chỗ lúc này. Vui lòng thử lại sau."
            );
        });

        if (outcome instanceof LookupNotFound) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy lượt đặt chỗ");
        }
        if (outcome instanceof LookupForbidden) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền khiếu nại lượt đặt chỗ này");
        }
        if (outcome instanceof LookupUnauthorized) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Phiên xác thực không hợp lệ");
        }
        if (outcome instanceof LookupSuccess(VerifiedBooking booking)) {
            return booking;
        }
        throw new AppException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Không thể xác minh thông tin lượt đặt chỗ lúc này. Vui lòng thử lại sau."
        );
    }

    private String extractBearerToken() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Thiếu phiên xác thực để xác minh lượt đặt chỗ");
        }
        String authHeader = servletAttributes.getRequest().getHeader(SecurityConstants.AUTH_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Thiếu token xác thực để xác minh lượt đặt chỗ");
        }
        String rawToken = authHeader.substring(SecurityConstants.BEARER_PREFIX.length());
        if (!StringUtils.hasText(rawToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Token xác thực không hợp lệ");
        }
        return authHeader;
    }
}

