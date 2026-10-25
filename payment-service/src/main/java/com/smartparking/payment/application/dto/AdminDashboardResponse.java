package com.smartparking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {
    private long totalUsers;
    private long totalDrivers;
    private long totalMerchants;
    private long totalParkingLots;
    private long pendingParkingLots;
    private long totalBookings;
    private long pendingWithdrawals;
    private long openDisputes;
    private double platformRevenue;
}
