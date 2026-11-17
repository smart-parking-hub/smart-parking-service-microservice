package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.LedgerJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerJournalRepository extends JpaRepository<LedgerJournal, Long> {

    Optional<LedgerJournal> findByJournalKey(String journalKey);

    boolean existsByJournalKey(String journalKey);

    List<LedgerJournal> findByMerchantIdOrderByCreatedAtAsc(Long merchantId);

    List<LedgerJournal> findByPaymentId(Long paymentId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "postings")
    List<LedgerJournal> findByPaymentIdIn(java.util.Collection<Long> paymentIds);

    List<LedgerJournal> findByBookingId(Long bookingId);
}

