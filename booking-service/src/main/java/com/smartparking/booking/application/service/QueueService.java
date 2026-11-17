package com.smartparking.booking.application.service;

import com.smartparking.booking.application.dto.JoinQueueRequest;
import com.smartparking.booking.application.dto.QueueDto;

import com.smartparking.common.enums.VehicleTypeEnum;

public interface QueueService {
    QueueDto joinQueue(Long driverUserId, JoinQueueRequest request);
    QueueDto getMyQueue(Long driverUserId);
    void cancelQueue(Long driverUserId, Long queueId);
    void promoteNextInQueue(Long parkingLotId, VehicleTypeEnum vehicleType);
    boolean tryPromoteNextInQueue(Long parkingLotId, VehicleTypeEnum vehicleType);
}
