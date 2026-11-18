package com.smartparking.booking.infrastructure.client;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "parking-lot-service", url = "${services.parking-lot.url:http://localhost:8082}", configuration = ParkingLotClient.InternalConfig.class)
public interface ParkingLotClient {
    record LegacyReservationSnapshot(Long bookingId, com.smartparking.common.enums.VehicleTypeEnum vehicleType,
            String physicalSlotId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
            java.time.LocalDateTime expiresAt, java.time.LocalDateTime checkInTime, java.time.LocalDateTime checkoutTime,
            String status, int earlyMinutes) {}
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/import")
    ApiResponse<Void> prepareLegacyImport(@PathVariable("id") Long id, @RequestBody LegacyReservationSnapshot snapshot);
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/activate-import")
    ApiResponse<Void> activateLegacyImport(@PathVariable("id") Long id, @PathVariable("bookingId") Long bookingId);
    record ReservationAvailability(int remainingCapacity) {}
    @org.springframework.web.bind.annotation.GetMapping("/api/v1/parking-lots/{id}/availability")
    ApiResponse<ReservationAvailability> available(@PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestParam("vehicleType") com.smartparking.common.enums.VehicleTypeEnum type,
            @org.springframework.web.bind.annotation.RequestParam("start") String start,
            @org.springframework.web.bind.annotation.RequestParam("end") String end);
    class InternalConfig {
        @org.springframework.context.annotation.Bean
        feign.Request.Options parkingLotRequestOptions() {
            return new feign.Request.Options(3, java.util.concurrent.TimeUnit.SECONDS, 5, java.util.concurrent.TimeUnit.SECONDS, true);
        }
        @org.springframework.context.annotation.Bean
        feign.Capability parkingLotResilience(org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory<?, ?> breakers) {
            return new ParkingLotResilienceCapability(breakers.create("parking-lot-rpc"));
        }
        @org.springframework.context.annotation.Bean
        feign.RequestInterceptor internalServiceHeader(@org.springframework.beans.factory.annotation.Value("${app.internal-service-token:}") String token) {
            return template -> template.header("X-Internal-Service-Token", token);
        }
    }


    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations")
    ApiResponse<SlotHoldResponse> holdSlot(
            @PathVariable("id") Long parkingLotId,
            @RequestBody SlotHoldRequest request);

    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/release")
    ApiResponse<Void> releaseSlot(
            @PathVariable("id") Long parkingLotId,
            @RequestBody SlotHoldRequest request);

    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/confirm")
    ApiResponse<Void> confirmReservation(@PathVariable("id") Long id, @PathVariable("bookingId") Long bookingId);
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/occupy")
    ApiResponse<Void> occupyReservation(@PathVariable("id") Long id, @PathVariable("bookingId") Long bookingId, @RequestBody java.util.Map<String, String> request);
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/abort-occupation")
    ApiResponse<Void> abortOccupation(@PathVariable("id") Long id, @PathVariable("bookingId") Long bookingId, @RequestBody java.util.Map<String, String> request);
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/complete")
    ApiResponse<Void> completeReservation(@PathVariable("id") Long id, @PathVariable("bookingId") Long bookingId,
            @RequestBody java.util.Map<String, java.time.LocalDateTime> request);

    @PostMapping("/api/v1/internal/parking-lots/{id}/rating")
    ApiResponse<Void> updateRating(
            @PathVariable("id") Long parkingLotId,
            @org.springframework.web.bind.annotation.RequestParam("rating") Double rating);
}
