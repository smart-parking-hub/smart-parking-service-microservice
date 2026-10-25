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

import com.smartparking.common.dto.PageResponse;
import java.util.List;

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

    @GetMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<PageResponse<QueueDto>>> getMyQueues(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueueDto current = queueService.getMyQueue(user.getId());
        List<QueueDto> list = current != null ? List.of(current) : List.of();
        PageResponse<QueueDto> pageResponse = PageResponse.<QueueDto>builder()
                .content(list)
                .currentPage(0)
                .totalPages(list.isEmpty() ? 0 : 1)
                .totalElements((long) list.size())
                .build();
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<QueueDto>> getMyQueue(
            @AuthenticationPrincipal AuthenticatedUser user) {
        QueueDto dto = queueService.getMyQueue(user.getId());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping({"/{id}/cancel", "/{id}/leave", "/{id}/decline"})
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Void>> cancelQueue(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        queueService.cancelQueue(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã hủy lượt xếp hàng thành công"));
    }
}
