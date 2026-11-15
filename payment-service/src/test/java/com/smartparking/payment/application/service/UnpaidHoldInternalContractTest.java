package com.smartparking.payment.application.service;

import com.smartparking.payment.api.controller.InternalUnpaidHoldController;
import com.smartparking.payment.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UnpaidHoldInternalContractTest {
    @Test void assessmentIsNotAvailableUsingUserHeadersOrAnIncorrectToken() throws Exception {
        var service=mock(UnpaidHoldAssessmentService.class);
        var controller=new InternalUnpaidHoldController(service);
        ReflectionTestUtils.setField(controller,"serviceToken","test-hold-service-token");
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        String path="/api/v1/internal/payments/41/unpaid-hold-assessment";
        mvc.perform(post(path).header("X-User-Id","7").header("X-User-Role","ADMIN")).andExpect(status().isForbidden());
        mvc.perform(post(path).header("X-Internal-Service-Token","wrong")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(service.assess(41L)).thenReturn(UnpaidHoldAssessmentService.Outcome.UNKNOWN);
        mvc.perform(post(path).header("X-Internal-Service-Token","test-hold-service-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.disposition").value("UNKNOWN"));
        ReflectionTestUtils.setField(controller,"serviceToken","");
        mvc.perform(post(path).header("X-Internal-Service-Token","")).andExpect(status().isForbidden());
        verify(service,times(1)).assess(41L);
    }
}
