package com.smartparking.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "merchants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merchant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "citizen_id", nullable = false, unique = true, length = 12)
    private String citizenId;

    @Column(name = "business_license", length = 50)
    private String businessLicense;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "bank_account_num", nullable = false, length = 30)
    private String bankAccountNum;

    @Column(name = "wallet_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal walletBalance = BigDecimal.ZERO;
}
