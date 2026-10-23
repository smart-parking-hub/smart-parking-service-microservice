package com.smartparking.booking.domain.repository;

import com.smartparking.booking.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByParkingLotIdOrderByCreatedAtDesc(Long parkingLotId, Pageable pageable);

    boolean existsByBookingId(Long bookingId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.parkingLotId = :parkingLotId")
    Optional<Double> findAverageRatingByParkingLotId(@Param("parkingLotId") Long parkingLotId);
}
