package com.smartparking.booking.application.service;

import com.smartparking.booking.config.HoldPolicyProperties;
import com.smartparking.booking.domain.HoldRestrictionException;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HoldRateLimiter {
    private final HoldPolicyStore store;
    private final HoldPolicyProperties properties;
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void acquire(Long driverId, LocalDateTime now) {
        if (!properties.isEnabled()) return;
        store.lock("RATE:" + driverId);
        var window=store.rateWindow(driverId).orElse(new HoldPolicyStore.RateWindow(now,0));
        LocalDateTime until=window.startedAt().plusSeconds(properties.getRateWindowSeconds());
        if (!now.isBefore(until)) window=new HoldPolicyStore.RateWindow(now,0);
        if (window.count()>=properties.getRateLimit()) throw new HoldRestrictionException(
                HoldRestrictionException.Reason.RATE_LIMIT,"Bạn đang tạo giữ chỗ quá nhanh. Vui lòng chờ rồi thử lại.",until,null);
        store.setRateWindow(driverId,window.startedAt(),window.count()+1);
    }
}
