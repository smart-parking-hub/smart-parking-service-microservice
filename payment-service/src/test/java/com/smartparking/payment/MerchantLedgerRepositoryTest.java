package com.smartparking.payment;

import com.smartparking.common.enums.WalletLedgerEntryTypeEnum;
import com.smartparking.payment.domain.model.WalletLedgerEntry;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class MerchantLedgerRepositoryTest {
    @Autowired private WalletLedgerEntryRepository repository;

    @Test
    void filtersMerchantAndPaginatesTiedTimestampsInStableOrder() {
        var older = repository.saveAndFlush(entry(7L));
        var newer = repository.saveAndFlush(entry(7L));
        repository.saveAndFlush(entry(8L));
        var first = repository.findByMerchantIdOrderByCreatedAtDesc(7L,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));
        var second = repository.findByMerchantIdOrderByCreatedAtDesc(7L,
                PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "id")));
        assertEquals(2, first.getTotalElements());
        assertEquals(newer.getId(), first.getContent().getFirst().getId());
        assertEquals(older.getId(), second.getContent().getFirst().getId());
        assertTrue(repository.findByMerchantIdOrderByCreatedAtDesc(9L, PageRequest.of(0, 20)).isEmpty());
    }

    private WalletLedgerEntry entry(Long merchantId) {
        return WalletLedgerEntry.builder().merchantId(merchantId)
                .entryType(WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT)
                .grossAmount(new BigDecimal("100000")).feePercent(BigDecimal.TEN)
                .feeAmount(new BigDecimal("10000")).netAmount(new BigDecimal("90000"))
                .createdAt(LocalDateTime.of(2026, 11, 14, 9, 30)).build();
    }
}
