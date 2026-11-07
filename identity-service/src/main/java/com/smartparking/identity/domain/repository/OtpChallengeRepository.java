package com.smartparking.identity.domain.repository;

import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.identity.domain.model.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findByRecipientAndPurpose(String recipient, OtpPurposeEnum purpose);
}
