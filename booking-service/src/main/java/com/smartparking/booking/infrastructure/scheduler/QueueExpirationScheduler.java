package com.smartparking.booking.infrastructure.scheduler;

import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.domain.model.Queue;
import com.smartparking.booking.domain.repository.QueueRepository;
import com.smartparking.common.enums.QueueStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueExpirationScheduler {

    private final QueueRepository queueRepository;
    private final QueueService queueService;

    /**
     * Runs every 60 seconds to expire NOTIFIED queue entries whose response deadline has elapsed.
     * Automatically promotes the next waiting driver in queue.
     */
    @Scheduled(cron = "15 * * * * *")
    @Transactional
    public void expireNotifiedQueues() {
        LocalDateTime now = LocalDateTime.now();
        List<Queue> expiredQueues = queueRepository.findByStatusAndResponseDeadlineBefore(
                QueueStatusEnum.NOTIFIED, now);

        if (expiredQueues.isEmpty()) {
            return;
        }

        log.info("Found {} expired NOTIFIED queue entries to process", expiredQueues.size());

        for (Queue queue : expiredQueues) {
            try {
                queue.setStatus(QueueStatusEnum.EXPIRED);
                queueRepository.save(queue);

                log.info("Queue entry {} for driver {} expired past deadline at lot {}. Promoting next driver.",
                        queue.getId(), queue.getDriverId(), queue.getParkingLotId());

                // Promote the next waiting driver
                queueService.promoteNextInQueue(queue.getParkingLotId(), queue.getVehicleType());

            } catch (Exception e) {
                log.error("Failed to expire queue entry {}: {}", queue.getId(), e.getMessage());
            }
        }
    }
}
