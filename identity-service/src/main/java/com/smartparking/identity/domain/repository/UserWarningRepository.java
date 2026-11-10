package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.UserWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserWarningRepository extends JpaRepository<UserWarning, Long> {
    List<UserWarning> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserId(Long userId);
}

