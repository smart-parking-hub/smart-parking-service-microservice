package com.smartparking.booking.domain.repository;

import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.booking.domain.model.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByRequestKey(String requestKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.requestKey = :key")
    Optional<Booking> findByRequestKeyForUpdate(@Param("key") String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
    Optional<Booking> findByBookingCode(String bookingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.bookingCode = :bookingCode")
    Optional<Booking> findByBookingCodeForUpdate(@Param("bookingCode") String bookingCode);

    Page<Booking> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE b.driverId = :driverId " +
           "AND (:status IS NULL OR b.status = :status) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(b.bookingCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(b.vehiclePlate) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(b.parkingLotName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (CAST(:fromTime AS java.time.LocalDateTime) IS NULL OR b.expectedCheckInTime >= :fromTime) " +
           "AND (CAST(:toTime AS java.time.LocalDateTime) IS NULL OR b.expectedCheckInTime <= :toTime) " +
           "ORDER BY b.createdAt DESC")
    Page<Booking> searchDriverBookings(
            @Param("driverId") Long driverId,
            @Param("status") BookingStatusEnum status,
            @Param("search") String search,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable);

    Page<Booking> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    Page<Booking> findByMerchantIdAndParkingLotIdOrderByCreatedAtDesc(Long merchantId, Long parkingLotId, Pageable pageable);

    long countByParkingLotIdAndStatusIn(Long parkingLotId, Collection<BookingStatusEnum> statuses);

    List<Booking> findByStatusAndExpiresAtBefore(BookingStatusEnum status, LocalDateTime dateTime);
}
