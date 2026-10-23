package com.smartparking.identity.domain.repository;

import com.smartparking.identity.domain.model.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Page<Vehicle> findByDriverId(Long driverId, Pageable pageable);
    Optional<Vehicle> findByLicensePlate(String licensePlate);
    boolean existsByLicensePlate(String licensePlate);
}
