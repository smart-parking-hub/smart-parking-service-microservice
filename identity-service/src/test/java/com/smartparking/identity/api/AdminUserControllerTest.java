package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.identity.application.dto.AdminDecisionRequest;
import com.smartparking.identity.application.dto.AdminUserDetailResponse;
import com.smartparking.identity.application.dto.AdminUserResponse;
import com.smartparking.identity.application.dto.AuditLogResponse;
import com.smartparking.identity.application.service.UserService;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserController controller;

    private CustomUserDetails admin;

    @BeforeEach
    void setUp() {
        admin = new CustomUserDetails(1L, "0987654321", "pass", UserStatusEnum.ACTIVE, Collections.emptyList());
    }

    @Test
    @DisplayName("listUsers returns page of users")
    void testListUsers() {
        PageResponse<AdminUserResponse> pageResponse = new PageResponse<>(List.of(
                AdminUserResponse.builder().id(2L).phone("0912345678").build()
        ), 0, 1, 1);
        when(userService.getAllUsersForAdmin(any())).thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> res = controller.listUsers(PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().getData().getContent().size());
    }

    @Test
    @DisplayName("userDetail returns user detail")
    void testUserDetail() {
        AdminUserDetailResponse detail = AdminUserDetailResponse.builder().id(2L).fullName("Test").build();
        when(userService.getUserDetailForAdmin(2L)).thenReturn(detail);

        ResponseEntity<ApiResponse<AdminUserDetailResponse>> res = controller.userDetail(2L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("Test", res.getBody().getData().getFullName());
    }

    @Test
    @DisplayName("banUser delegates to userService")
    void testBanUser() {
        AdminDecisionRequest req = new AdminDecisionRequest("Vi pham");
        ResponseEntity<ApiResponse<Void>> res = controller.banUser(admin, 2L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService).banUser(1L, 2L, "Vi pham");
    }

    @Test
    @DisplayName("banUser handles null request")
    void testBanUserNullRequest() {
        ResponseEntity<ApiResponse<Void>> res = controller.banUser(admin, 2L, null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService).banUser(1L, 2L, null);
    }

    @Test
    @DisplayName("unbanUser delegates to userService")
    void testUnbanUser() {
        AdminDecisionRequest req = new AdminDecisionRequest("Mo khoa");
        ResponseEntity<ApiResponse<Void>> res = controller.unbanUser(admin, 2L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService).unbanUser(1L, 2L, "Mo khoa");
    }

    @Test
    @DisplayName("unbanUser handles null request")
    void testUnbanUserNullRequest() {
        ResponseEntity<ApiResponse<Void>> res = controller.unbanUser(admin, 2L, null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService).unbanUser(1L, 2L, null);
    }

    @Test
    @DisplayName("warnUser delegates to userService")
    void testWarnUser() {
        AdminDecisionRequest req = new AdminDecisionRequest("Canh bao");
        ResponseEntity<ApiResponse<Void>> res = controller.warnUser(admin, 2L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService).warnUser(1L, 2L, "Canh bao");
    }

    @Test
    @DisplayName("warnUser handles null request")
    void testWarnUserNullRequest() {
        ResponseEntity<ApiResponse<Void>> res = controller.warnUser(admin, 2L, null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService).warnUser(1L, 2L, null);
    }

    @Test
    @DisplayName("auditLogs returns page of audit logs")
    void testAuditLogs() {
        PageResponse<AuditLogResponse> pageResponse = new PageResponse<>(List.of(
                AuditLogResponse.builder().id(1L).action("BAN_USER").build()
        ), 0, 1, 1);
        when(userService.getAuditLogs(any())).thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> res = controller.auditLogs(PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().getData().getContent().size());
    }
}
