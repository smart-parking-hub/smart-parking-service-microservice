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
    @Query("SELECT b.id FROM Booking b WHERE b.schedulePolicyVersion IS NOT NULL AND b.scheduleQueueNotifiedAt IS NULL "
            + "AND b.id > :afterId AND b.checkOutTime <= :cutoff AND b.expectedCheckOutTime <= :cutoff ORDER BY b.id")
    List<Long> findSchedulesReadyForQueue(@Param("cutoff") LocalDateTime cutoff, @Param("afterId") Long afterId, Pageable pageable);

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

    @Query(value = "SELECT b FROM Booking b WHERE b.driverId = :driverId " +
           "AND (:status IS NULL OR b.status = :status) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     CONCAT('', b.id) LIKE CONCAT('%', :search, '%') OR " +
           "     LOWER(b.bookingCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(b.vehiclePlate) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(b.parkingLotName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:fromTime IS NULL OR b.expectedCheckInTime >= :fromTime) " +
           "AND (:toTime IS NULL OR b.expectedCheckInTime <= :toTime) " +
           "ORDER BY b.createdAt DESC",
           countQuery = "SELECT count(b) FROM Booking b WHERE b.driverId = :driverId " +
           "AND (:status IS NULL OR b.status = :status) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     CONCAT('', b.id) LIKE CONCAT('%', :search, '%') OR " +
           "     LOWER(b.bookingCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(b.vehiclePlate) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(b.parkingLotName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:fromTime IS NULL OR b.expectedCheckInTime >= :fromTime) " +
           "AND (:toTime IS NULL OR b.expectedCheckInTime <= :toTime)")
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

    interface StatusCountProjection {
        BookingStatusEnum getStatus();
        Long getCount();
    }

    @Query("SELECT b.status AS status, COUNT(b) AS count FROM Booking b " +
           "WHERE b.merchantId = :merchantId AND (:parkingLotId IS NULL OR b.parkingLotId = :parkingLotId) " +
           "GROUP BY b.status")
    List<StatusCountProjection> countByMerchantAndOptionalLotGroupedByStatus(
            @Param("merchantId") Long merchantId,
            @Param("parkingLotId") Long parkingLotId);

    List<Booking> findByStatusAndExpiresAtBefore(BookingStatusEnum status, LocalDateTime dateTime);
}
