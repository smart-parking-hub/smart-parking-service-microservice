package com.smartparking.booking.application.service;

import com.smartparking.booking.domain.HoldRestrictionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.function.Supplier;

/** Rate checks and vehicle HTTP verification run before this transaction acquires quota locks. */
@Service
public class BookingCreationTransaction {
    // Quota key locks serialize conflicting creates. READ_COMMITTED avoids MySQL gap locks
    // coupling independent drivers while still seeing the previous lock owner's commit.
    @Transactional(noRollbackFor = HoldRestrictionException.class, isolation = Isolation.READ_COMMITTED)
    public <T> T run(Supplier<T> create) { return create.get(); }
}
