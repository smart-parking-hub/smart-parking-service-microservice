package com.smartparking.payment.domain.repository;

import com.smartparking.common.enums.WithdrawalStatusEnum;
import com.smartparking.payment.domain.model.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from WithdrawalRequest w where w.id = :id")
    java.util.Optional<WithdrawalRequest> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Page<WithdrawalRequest> findByMerchantIdOrderByRequestedAtDesc(Long merchantId, Pageable pageable);

    Page<WithdrawalRequest> findByStatusOrderByRequestedAtAsc(WithdrawalStatusEnum status, Pageable pageable);

    long countByStatus(WithdrawalStatusEnum status);
}
