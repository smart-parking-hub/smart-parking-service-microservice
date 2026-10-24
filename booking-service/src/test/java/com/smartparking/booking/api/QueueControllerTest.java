package com.smartparking.booking.api;

import com.smartparking.booking.api.controller.QueueController;
import com.smartparking.booking.application.dto.JoinQueueRequest;
import com.smartparking.booking.application.dto.QueueDto;
import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.infrastructure.security.AuthenticatedUser;
import com.smartparking.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueControllerTest {

    @Mock
    private QueueService queueService;

    @InjectMocks
    private QueueController queueController;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(100L, "driver01", "DRIVER");
    }

    @Test
    @DisplayName("joinQueue returns CREATED status")
    void testJoinQueue() {
        JoinQueueRequest req = new JoinQueueRequest();
        QueueDto dto = QueueDto.builder().id(1L).build();
        when(queueService.joinQueue(100L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<QueueDto>> response = queueController.joinQueue(user, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("getMyQueue returns OK status")
    void testGetMyQueue() {
        QueueDto dto = QueueDto.builder().id(1L).build();
        when(queueService.getMyQueue(100L)).thenReturn(dto);

        ResponseEntity<ApiResponse<QueueDto>> response = queueController.getMyQueue(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("cancelQueue delegates to queueService")
    void testCancelQueue() {
        ResponseEntity<ApiResponse<Void>> response = queueController.cancelQueue(user, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(queueService, times(1)).cancelQueue(100L, 1L);
    }
}
