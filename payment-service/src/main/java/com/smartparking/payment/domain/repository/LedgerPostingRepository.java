package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.LedgerAccountCode;
import com.smartparking.payment.domain.model.LedgerPosting;
import com.smartparking.payment.domain.model.PostingDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerPostingRepository extends JpaRepository<LedgerPosting, Long> {

    List<LedgerPosting> findByAccountCodeAndMerchantId(LedgerAccountCode accountCode, Long merchantId);

    @Query("SELECT COALESCE(SUM(CASE WHEN p.direction = 'CREDIT' THEN p.amount ELSE -p.amount END), 0) " +
           "FROM LedgerPosting p WHERE p.accountCode = :accountCode AND p.merchantId = :merchantId")
    BigDecimal sumCreditMinusDebitByAccountAndMerchant(
            @Param("accountCode") LedgerAccountCode accountCode,
            @Param("merchantId") Long merchantId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM LedgerPosting p WHERE p.direction = :direction")
    BigDecimal sumTotalByDirection(@Param("direction") PostingDirection direction);
}

