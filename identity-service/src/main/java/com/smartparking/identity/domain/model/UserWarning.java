package com.smartparking.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_warnings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWarning extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "penalty_type", nullable = false, length = 50)
    @Builder.Default
    private String penaltyType = "OFFICIAL_WARNING";

    @Column(name = "notify_user", nullable = false)
    @Builder.Default
    private Boolean notifyUser = true;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Column(name = "issued_by_admin_id", nullable = false)
    private Long issuedByAdminId;
}

