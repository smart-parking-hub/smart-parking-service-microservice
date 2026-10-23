package com.smartparking.booking.api.controller;

import com.smartparking.booking.application.dto.JoinQueueRequest;
import com.smartparking.booking.application.dto.QueueDto;
import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.infrastructure.security.AuthenticatedUser;
import com.smartparking.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<QueueDto>> joinQueue(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody JoinQueueRequest request) {
        QueueDto dto = queueService.joinQueue(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto, "Tham gia hàng đợi thành công"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<QueueDto>> getMyQueue(
            @AuthenticationPrincipal AuthenticatedUser user) {
        QueueDto dto = queueService.getMyQueue(user.getId());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping({"/{id}/cancel", "/{id}/leave"})
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Void>> cancelQueue(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        queueService.cancelQueue(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã hủy lượt xếp hàng thành công"));
    }
}
