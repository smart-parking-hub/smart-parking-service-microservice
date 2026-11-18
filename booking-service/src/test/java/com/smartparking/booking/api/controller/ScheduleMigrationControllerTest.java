package com.smartparking.booking.api.controller;
import com.smartparking.booking.application.service.LegacyScheduleAdoption;
import com.smartparking.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ScheduleMigrationControllerTest {
    @Test void anonymousOrWrongTokenCannotModifyOldBookings() {
        var service=mock(LegacyScheduleAdoption.class);var controller=new ScheduleMigrationController(service);
        ReflectionTestUtils.setField(controller,"serviceToken","internal-test");
        assertThrows(AppException.class,()->controller.adopt(1L,null));
        assertThrows(AppException.class,()->controller.adopt(1L,"wrong"));verifyNoInteractions(service);
        when(service.adopt(1L)).thenReturn(true);
        assertTrue(controller.adopt(1L,"internal-test").getData());
    }
}
