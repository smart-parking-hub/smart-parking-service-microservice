package com.smartparking.booking.application.service.impl;

import com.smartparking.common.enums.QueueStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.booking.application.dto.JoinQueueRequest;
import com.smartparking.booking.application.dto.QueueDto;
import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.domain.model.Queue;
import com.smartparking.booking.domain.repository.QueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueServiceImpl implements QueueService {

    private final QueueRepository queueRepository;

    @Override
    @Transactional
    public QueueDto joinQueue(Long driverUserId, JoinQueueRequest request) {
        queueRepository.findByDriverIdAndStatus(driverUserId, QueueStatusEnum.WAITING).ifPresent(q -> {
            throw new AppException(ErrorCode.CONFLICT, "Bạn đang trong hàng đợi của một bãi đỗ xe khác");
        });

        Queue queue = Queue.builder()
                .driverId(driverUserId)
                .vehicleId(request.getVehicleId())
                .parkingLotId(request.getParkingLotId())
                .vehicleType(request.getVehicleType())
                .status(QueueStatusEnum.WAITING)
                .joinedAt(LocalDateTime.now())
                .build();

        Queue saved = queueRepository.save(queue);

        List<Queue> waitingList = queueRepository.findByParkingLotIdAndVehicleTypeAndStatusOrderByJoinedAtAsc(
                request.getParkingLotId(), request.getVehicleType(), QueueStatusEnum.WAITING);
        int position = waitingList.indexOf(saved) + 1;
        if (position <= 0) position = 1;

        log.info("Driver {} joined queue {} at lot {} position {}", driverUserId, saved.getId(), request.getParkingLotId(), position);
        return QueueDto.fromEntity(saved, position);
    }

    @Override
    @Transactional(readOnly = true)
    public QueueDto getMyQueue(Long driverUserId) {
        Queue queue = queueRepository.findByDriverIdAndStatus(driverUserId, QueueStatusEnum.WAITING)
                .orElse(null);

        if (queue == null) {
            return null;
        }

        List<Queue> waitingList = queueRepository.findByParkingLotIdAndVehicleTypeAndStatusOrderByJoinedAtAsc(
                queue.getParkingLotId(), queue.getVehicleType(), QueueStatusEnum.WAITING);
        int position = waitingList.indexOf(queue) + 1;
        if (position <= 0) position = 1;

        return QueueDto.fromEntity(queue, position);
    }

    @Override
    @Transactional
    public void cancelQueue(Long driverUserId, Long queueId) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy lượt xếp hàng"));

        if (!queue.getDriverId().equals(driverUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy lượt xếp hàng này");
        }

        queue.setStatus(QueueStatusEnum.CANCELLED);
        queueRepository.save(queue);
        log.info("Driver {} cancelled queue {}", driverUserId, queueId);
    }
}
