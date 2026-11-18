package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    Optional<Merchant> findByUserId(Long userId);
    Optional<Merchant> findByCitizenId(String citizenId);
    boolean existsByCitizenId(String citizenId);
}
