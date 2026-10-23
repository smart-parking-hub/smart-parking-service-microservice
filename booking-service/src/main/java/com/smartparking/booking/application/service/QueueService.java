package com.smartparking.booking.application.service;

import com.smartparking.booking.application.dto.JoinQueueRequest;
import com.smartparking.booking.application.dto.QueueDto;

public interface QueueService {
    QueueDto joinQueue(Long driverUserId, JoinQueueRequest request);
    QueueDto getMyQueue(Long driverUserId);
    void cancelQueue(Long driverUserId, Long queueId);
}
