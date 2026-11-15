package com.smartparking.booking.infrastructure.scheduler;

import com.smartparking.booking.application.service.HoldAbandonmentVerifier;
import com.smartparking.booking.config.HoldPolicyProperties;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="app.booking.hold-policy.recheck-enabled",havingValue="true",matchIfMissing=true)
public class HoldAbuseRecheckScheduler {
    private final HoldPolicyStore store;
    private final HoldAbandonmentVerifier verifier;
    private final HoldPolicyProperties properties;
    @Scheduled(fixedDelayString="${app.booking.hold-policy.recheck-delay-ms:60000}")
    public void recheck() {
        if (!properties.isEnabled()) return;
        var now=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for (Long id:store.dueForRecheck(now.minusMinutes((long)properties.getWindowMinutes()+properties.getCooldownMinutes()),now.minusMinutes(1))) {
            try { verifier.verify(id,now); }
            catch (Exception failure) { log.warn("Could not recheck abandoned hold {}",id); }
        }
    }
}
