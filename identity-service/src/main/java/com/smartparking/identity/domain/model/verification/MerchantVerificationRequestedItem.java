package com.smartparking.identity.domain.model.verification;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "merchant_verification_requested_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantVerificationRequestedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private MerchantVerificationSubmission submission;

    @Column(name = "item_key", nullable = false, length = 50)
    private String itemKey;

    @Column(nullable = false, length = 1000)
    private String reason;
}
