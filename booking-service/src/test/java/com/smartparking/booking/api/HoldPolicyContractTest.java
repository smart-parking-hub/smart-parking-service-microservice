package com.smartparking.booking.api;

import com.smartparking.booking.api.controller.InternalHoldPolicyController;
import com.smartparking.booking.application.service.HoldStateQuery;
import com.smartparking.booking.domain.HoldRestrictionException;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import com.smartparking.common.enums.BookingStatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HoldPolicyContractTest {
    @Test void publicRestrictionsReturnTypedDetailsAndServerRetryHeader() {
        var handler=new GlobalExceptionHandler();
        for(var reason:HoldRestrictionException.Reason.values()) {
            boolean timed=reason==HoldRestrictionException.Reason.COOLDOWN || reason==HoldRestrictionException.Reason.RATE_LIMIT
                    || reason==HoldRestrictionException.Reason.PAYMENT_CHECK_PENDING;
            var until=timed ? LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMinutes(30) : null;
            var response=handler.handleHoldRestriction(new HoldRestrictionException(reason,"Thông báo",until,41L));
            assertEquals(reason==HoldRestrictionException.Reason.PAYMENT_CHECK_PENDING?503:timed?429:409,response.getStatusCode().value());
            assertNotNull(response.getBody());assertFalse(response.getBody().isSuccess());
            assertEquals("BOOKING_HOLD_"+reason,response.getBody().getCode());
            assertEquals(until,response.getBody().getData().retryAt());
            assertEquals(41L,response.getBody().getData().existingBookingId());
            if(timed) assertTrue(Long.parseLong(response.getHeaders().getFirst("Retry-After"))>0);
            else assertNull(response.getHeaders().getFirst("Retry-After"));
        }
    }
    @Test void internalStateRequiresActualServiceTokenAndNeverLocksBookingRows() throws Exception {
        var repo=mock(BookingRepository.class);var store=mock(HoldPolicyStore.class);
        var query=spy(new HoldStateQuery(repo,store));
        var controller=new InternalHoldPolicyController(query);
        ReflectionTestUtils.setField(controller,"serviceToken","test-hold-service-token");
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        String path="/api/v1/internal/bookings/41/unpaid-hold-state";
        mvc.perform(get(path).header("X-User-Id","7").header("X-User-Role","ADMIN")).andExpect(status().isForbidden());
        mvc.perform(get(path).header("X-Internal-Service-Token","wrong")).andExpect(status().isForbidden());
        verifyNoInteractions(repo,store);
        when(repo.findById(41L)).thenReturn(Optional.of(Booking.builder().id(41L).driverId(7L).status(BookingStatusEnum.EXPIRED).build()));
        when(store.hasRelease(41L)).thenReturn(true);
        mvc.perform(get(path).header("X-Internal-Service-Token","test-hold-service-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingId").value(41))
                .andExpect(jsonPath("$.data.unpaidReleaseCandidate").value(true)).andExpect(jsonPath("$.data.status").value("EXPIRED"));
        verify(repo,never()).findByIdForUpdate(anyLong());
        ReflectionTestUtils.setField(controller,"serviceToken","");
        mvc.perform(get(path).header("X-Internal-Service-Token","")).andExpect(status().isForbidden());
    }
}
