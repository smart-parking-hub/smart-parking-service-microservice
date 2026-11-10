package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {

    Optional<SettlementBatch> findByBusinessDateAndMerchantId(LocalDate businessDate, Long merchantId);

    Optional<SettlementBatch> findByBatchKey(String batchKey);

    List<SettlementBatch> findByBusinessDate(LocalDate businessDate);
}

