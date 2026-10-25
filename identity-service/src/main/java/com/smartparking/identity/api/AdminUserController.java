package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.identity.application.dto.AdminDecisionRequest;
import com.smartparking.identity.application.dto.AdminUserDetailResponse;
import com.smartparking.identity.application.dto.AdminUserResponse;
import com.smartparking.identity.application.dto.AuditLogResponse;
import com.smartparking.identity.application.service.UserService;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getAllUsersForAdmin(pageable),
                "Lấy danh sách người dùng thành công"));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> userDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getUserDetailForAdmin(id),
                "Lấy chi tiết người dùng thành công"));
    }

    @PutMapping("/users/{id}/ban")
    public ResponseEntity<ApiResponse<Void>> banUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        String reason = request != null ? request.getReason() : null;
        userService.banUser(userDetails.getId(), id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã khóa tài khoản"));
    }

    @PutMapping("/users/{id}/unban")
    public ResponseEntity<ApiResponse<Void>> unbanUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        String reason = request != null ? request.getReason() : null;
        userService.unbanUser(userDetails.getId(), id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã mở khóa tài khoản"));
    }

    @PostMapping("/users/{id}/warnings")
    public ResponseEntity<ApiResponse<Void>> warnUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        String reason = request != null ? request.getReason() : null;
        userService.warnUser(userDetails.getId(), id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gửi cảnh báo"));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> auditLogs(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getAuditLogs(pageable),
                "Lấy lịch sử thao tác thành công"));
    }
}
