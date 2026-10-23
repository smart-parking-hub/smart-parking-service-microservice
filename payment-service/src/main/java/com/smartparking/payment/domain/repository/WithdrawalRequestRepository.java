package com.smartparking.payment.domain.repository;

import com.smartparking.common.enums.WithdrawalStatusEnum;
import com.smartparking.payment.domain.model.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

    Page<WithdrawalRequest> findByMerchantIdOrderByRequestedAtDesc(Long merchantId, Pageable pageable);

    Page<WithdrawalRequest> findByStatusOrderByRequestedAtAsc(WithdrawalStatusEnum status, Pageable pageable);
}
