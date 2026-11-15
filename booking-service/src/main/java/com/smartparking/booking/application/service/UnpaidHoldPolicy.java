package com.smartparking.booking.application.service;

import com.smartparking.booking.config.HoldPolicyProperties;
import com.smartparking.booking.domain.HoldRestrictionException;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class UnpaidHoldPolicy {
    private final HoldPolicyStore store;
    private final HoldPolicyProperties properties;
    private final HoldAbandonmentVerifier verifier;
    public void lock(Long driverId, Long vehicleId) {
        if (!properties.isEnabled()) return;
        store.lock("ACCOUNT:"+driverId);
        store.lock("VEHICLE:"+vehicleId);
    }
    /** Called inside BookingCreationTransaction, after locking and checking replay again. */
    public void check(Long driverId, Long vehicleId, Long lotId, LocalDateTime now) {
        if (!properties.isEnabled()) return;
        var vehicleHolds=store.activeForVehicle(vehicleId);
        if (!vehicleHolds.isEmpty()) {
            var hold=vehicleHolds.getFirst();
            throw new HoldRestrictionException(HoldRestrictionException.Reason.VEHICLE_LIMIT,
                    "Xe này đang có một vé chờ thanh toán cọc. Vui lòng xử lý vé đang giữ trước khi đặt tiếp.",null,
                    driverId.equals(hold.driverId()) ? hold.id() : null);
        }
        var accountHolds=store.activeForDriver(driverId);
        if (accountHolds.size()>=properties.getMaxPendingPerAccount()) throw new HoldRestrictionException(
                HoldRestrictionException.Reason.ACCOUNT_LIMIT,"Tài khoản đã có "+properties.getMaxPendingPerAccount()
                +" vé chờ thanh toán cọc. Vui lòng xử lý các vé đang giữ trước khi đặt thêm.",null,null);
        var releases=store.recentReleasesForUpdate(driverId,lotId,
                now.minusMinutes((long)properties.getWindowMinutes()+properties.getCooldownMinutes()));
        var unpaid=new ArrayList<LocalDateTime>();
        var possible=new ArrayList<LocalDateTime>(); int unknown=0;
        for (var release:releases) {
            String assessment=verifier.verify(release.bookingId(),now);
            if ("UNPAID".equals(assessment)) { unpaid.add(release.releasedAt()); possible.add(release.releasedAt()); }
            else if (!"PROTECTED".equals(assessment)) { unknown++; possible.add(release.releasedAt()); }
        }
        int threshold=properties.getAbandonmentThreshold();
        LocalDateTime until=cooldownUntil(unpaid);
        if (until!=null && now.isBefore(until)) throw new HoldRestrictionException(HoldRestrictionException.Reason.COOLDOWN,
                "Bạn đã bỏ hoặc hủy "+threshold+" lượt giữ chỗ chưa thanh toán tại bãi này trong "+properties.getWindowMinutes()
                +" phút. Vui lòng chờ trước khi giữ chỗ mới.",until,null);
        LocalDateTime potentialUntil=cooldownUntil(possible);
        if (unknown>0 && potentialUntil!=null && now.isBefore(potentialUntil)) throw new HoldRestrictionException(
                HoldRestrictionException.Reason.PAYMENT_CHECK_PENDING,
                "Chưa xác minh được thanh toán của các lượt giữ chỗ trước. Đây chưa được tính là vi phạm; vui lòng kiểm tra lại sau.",
                now.plusMinutes(1),null);
    }
    private LocalDateTime cooldownUntil(ArrayList<LocalDateTime> releases) {
        releases.sort(LocalDateTime::compareTo);
        LocalDateTime until=null;
        int threshold=properties.getAbandonmentThreshold();
        for (int index=threshold-1;index<releases.size();index++) {
            var end=releases.get(index);
            if (!releases.get(index-threshold+1).isBefore(end.minusMinutes(properties.getWindowMinutes()))) {
                var candidate=end.plusMinutes(properties.getCooldownMinutes());
                if (until==null || candidate.isAfter(until)) until=candidate;
            }
        }
        return until;
    }
}
