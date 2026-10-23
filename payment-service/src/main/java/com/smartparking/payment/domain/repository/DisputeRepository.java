package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.Dispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    Page<Dispute> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

    Page<Dispute> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    Optional<Dispute> findByBookingId(Long bookingId);

    Page<Dispute> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
