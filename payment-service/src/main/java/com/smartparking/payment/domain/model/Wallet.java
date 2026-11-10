package com.smartparking.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, unique = true)
    private Long merchantId;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @Version
    @Builder.Default
    private Long version = 0L;

    public void setBalance(BigDecimal ignored) {
        throw new UnsupportedOperationException(
                "Direct balance modification is forbidden; all wallet mutations must go through balanced double-entry ledger journals");
    }

    public BigDecimal applyJournal(LedgerJournal journal) {
        if (journal == null) {
            throw new IllegalArgumentException("LedgerJournal is required to mutate wallet balance");
        }
        BigDecimal delta = journal.netMerchantPayableDelta(this.merchantId);
        BigDecimal current = this.balance != null ? this.balance : BigDecimal.ZERO;
        BigDecimal updated = current.add(delta);
        if (updated.signum() < 0) {
            throw new com.smartparking.common.exception.AppException(
                    com.smartparking.common.exception.ErrorCode.BAD_REQUEST,
                    "Số dư khả dụng không đủ để thực hiện bút toán!");
        }
        this.balance = updated;
        return this.balance;
    }
}
