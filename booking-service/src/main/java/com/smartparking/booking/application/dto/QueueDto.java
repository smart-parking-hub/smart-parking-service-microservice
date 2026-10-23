package com.smartparking.booking.application.dto;

import com.smartparking.common.enums.QueueStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.booking.domain.model.Queue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueDto {
    private Long id;
    private Long driverId;
    private Long parkingLotId;
    private Long vehicleId;
    private VehicleTypeEnum vehicleType;
    private QueueStatusEnum status;
    private LocalDateTime joinedAt;
    private int position;
    private int peopleAhead;
    private LocalDateTime responseDeadline;

    public static QueueDto fromEntity(Queue queue, int position) {
        return QueueDto.builder()
                .id(queue.getId())
                .driverId(queue.getDriverId())
                .parkingLotId(queue.getParkingLotId())
                .vehicleId(queue.getVehicleId())
                .vehicleType(queue.getVehicleType())
                .status(queue.getStatus())
                .joinedAt(queue.getJoinedAt())
                .position(position)
                .peopleAhead(Math.max(0, position - 1))
                .responseDeadline(queue.getResponseDeadline())
                .build();
    }
}
