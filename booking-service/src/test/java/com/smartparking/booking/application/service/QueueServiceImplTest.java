package com.smartparking.booking.application.service;

import com.smartparking.booking.application.dto.JoinQueueRequest;
import com.smartparking.booking.application.dto.QueueDto;
import com.smartparking.booking.application.service.impl.QueueServiceImpl;
import com.smartparking.booking.domain.model.Queue;
import com.smartparking.booking.domain.repository.QueueRepository;
import com.smartparking.common.enums.QueueStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceImplTest {

    @Mock
    private QueueRepository queueRepository;

    @InjectMocks
    private QueueServiceImpl queueService;

    private final Long driverUserId = 100L;
    private final Long parkingLotId = 20L;

    @Test
    @DisplayName("Join queue fails when driver is already waiting in another queue")
    void testJoinQueueConflict() {
        Queue existing = Queue.builder().id(1L).driverId(driverUserId).status(QueueStatusEnum.WAITING).build();
        when(queueRepository.findByDriverIdAndStatus(driverUserId, QueueStatusEnum.WAITING))
                .thenReturn(Optional.of(existing));

        JoinQueueRequest req = JoinQueueRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(5L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .build();

        AppException ex = assertThrows(AppException.class, () -> queueService.joinQueue(driverUserId, req));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Join queue successfully calculates FIFO position")
    void testJoinQueueSuccess() {
        when(queueRepository.findByDriverIdAndStatus(driverUserId, QueueStatusEnum.WAITING))
                .thenReturn(Optional.empty());

        Queue q1 = Queue.builder().id(1L).build();
        Queue saved = Queue.builder().id(2L).parkingLotId(parkingLotId).vehicleType(VehicleTypeEnum.CONTAINER).build();

        when(queueRepository.save(any(Queue.class))).thenReturn(saved);
        when(queueRepository.findByParkingLotIdAndVehicleTypeAndStatusOrderByJoinedAtAsc(
                parkingLotId, VehicleTypeEnum.CONTAINER, QueueStatusEnum.WAITING))
                .thenReturn(List.of(q1, saved));

        JoinQueueRequest req = JoinQueueRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(5L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .build();

        QueueDto dto = queueService.joinQueue(driverUserId, req);

        assertNotNull(dto);
        assertEquals(2, dto.getPosition());
    }

    @Test
    @DisplayName("Cancel queue fails when user is not the driver")
    void testCancelQueueForbidden() {
        Queue queue = Queue.builder().id(10L).driverId(999L).build();
        when(queueRepository.findById(10L)).thenReturn(Optional.of(queue));

        AppException ex = assertThrows(AppException.class, () -> queueService.cancelQueue(driverUserId, 10L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Cancel queue successfully marks status as CANCELLED")
    void testCancelQueueSuccess() {
        Queue queue = Queue.builder().id(10L).driverId(driverUserId).status(QueueStatusEnum.WAITING).build();
        when(queueRepository.findById(10L)).thenReturn(Optional.of(queue));

        queueService.cancelQueue(driverUserId, 10L);

        assertEquals(QueueStatusEnum.CANCELLED, queue.getStatus());
        verify(queueRepository, times(1)).save(queue);
    }
}
