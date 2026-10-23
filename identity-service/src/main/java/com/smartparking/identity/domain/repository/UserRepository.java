package com.smartparking.identity.domain.repository;

import com.smartparking.common.enums.RoleEnum;
import com.smartparking.identity.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Boolean existsByPhone(String phone);
    Boolean existsByEmail(String email);
    Boolean existsByEmailIgnoreCase(String email);
    long countByRole(RoleEnum role);
}
