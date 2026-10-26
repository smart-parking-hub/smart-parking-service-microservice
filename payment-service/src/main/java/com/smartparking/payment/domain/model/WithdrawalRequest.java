package com.smartparking.payment.domain.model;

import com.smartparking.common.enums.WithdrawalStatusEnum;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawal_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WithdrawalStatusEnum status;

    @CreatedDate
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_num", length = 50)
    private String bankAccountNumber;

    @Column(name = "account_holder_name", length = 100)
    private String accountHolderName;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }
}
