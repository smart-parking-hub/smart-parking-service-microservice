package com.smartparking.parkinglot.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.ScheduledSlotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController @RequiredArgsConstructor
public class ScheduledReservationController {
    private final ScheduledSlotService schedules;
    private final com.smartparking.parkinglot.application.service.LegacyScheduleImport legacyImport;
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/import")
    public ApiResponse<Void> prepareImport(@PathVariable Long id, @RequestBody com.smartparking.parkinglot.application.service.LegacyScheduleImport.Snapshot source) {
        legacyImport.prepare(id, source); return ApiResponse.success(null);
    }
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/activate-import")
    public ApiResponse<Void> activateImport(@PathVariable Long id, @PathVariable Long bookingId) {
        legacyImport.activate(id, bookingId); return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/parking-lots/{id}/availability")
    public ApiResponse<ScheduleAvailability> available(@PathVariable Long id, @RequestParam VehicleTypeEnum vehicleType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String physicalSlotId) {
        return ApiResponse.success(schedules.available(id, vehicleType, start, end, physicalSlotId));
    }

    @GetMapping("/api/v1/parking-lots/{id}/timeline")
    public ApiResponse<ScheduleAvailability> timeline(@PathVariable Long id, @RequestParam VehicleTypeEnum vehicleType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String physicalSlotId) {
        return ApiResponse.success(schedules.timeline(id, vehicleType, date, physicalSlotId));
    }

    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations")
    public ApiResponse<SlotHoldResponse> hold(@PathVariable Long id, @Valid @RequestBody ScheduledHoldRequest request) {
        return ApiResponse.success(schedules.hold(id, request));
    }
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/confirm")
    public ApiResponse<Void> confirm(@PathVariable Long id, @PathVariable Long bookingId) {
        schedules.confirm(id, bookingId); return ApiResponse.success(null);
    }
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/occupy")
    public ApiResponse<Void> occupy(@PathVariable Long id, @PathVariable Long bookingId, @Valid @RequestBody Occupation request) {
        schedules.occupy(id, bookingId, request.token()); return ApiResponse.success(null);
    }
    public record Occupation(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="[a-zA-Z0-9-]{1,36}") String token) {}
    public record Checkout(@NotNull LocalDateTime checkoutTime) {}
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/abort-occupation")
    public ApiResponse<Void> abortOccupation(@PathVariable Long id, @PathVariable Long bookingId, @Valid @RequestBody Occupation request) {
        schedules.abortOccupation(id, bookingId, request.token()); return ApiResponse.success(null);
    }
    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/reservations/{bookingId}/complete")
    public ApiResponse<Void> complete(@PathVariable Long id, @PathVariable Long bookingId, @Valid @RequestBody Checkout request) {
        schedules.complete(id, bookingId, request.checkoutTime()); return ApiResponse.success(null);
    }
}
