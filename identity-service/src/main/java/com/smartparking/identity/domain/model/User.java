package com.smartparking.identity.domain.model;

import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.enums.UserStatusEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    @Column(unique = true, length = 100)
    private String email;

    @Column(name = "email_verified_at")
    private java.time.LocalDateTime emailVerifiedAt;

    public void setEmail(String email) {
        if (!java.util.Objects.equals(this.email, email)) this.emailVerifiedAt = null;
        this.email = email;
    }

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleEnum role;

    @Column(name = "emergency_contact", length = 20)
    private String emergencyContact;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatusEnum status;

    @Column(name = "status_reason", length = 1000)
    private String statusReason;
}
