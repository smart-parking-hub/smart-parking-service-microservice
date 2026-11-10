package com.smartparking.payment.application.service;

import com.smartparking.payment.domain.model.SettlementBatch;

import java.time.LocalDate;
import java.util.List;

public interface DailySettlementService {
    List<SettlementBatch> runDailySettlement(LocalDate businessDate);
}

