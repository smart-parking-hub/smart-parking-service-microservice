package com.smartparking.payment.api;

import com.smartparking.payment.api.controller.MerchantRevenueController;
import com.smartparking.payment.api.controller.PaymentController;
import com.smartparking.payment.application.dto.MerchantRevenueResponse;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.application.service.PaymentService;
import com.smartparking.payment.infrastructure.security.InternalAuthFilter;
import com.smartparking.payment.infrastructure.security.JwtTokenValidator;
import com.smartparking.payment.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MerchantRevenueController.class, PaymentController.class})
@Import({SecurityConfig.class, InternalAuthFilter.class})
@TestPropertySource(properties = "internal.secret=rev-sec-test")
class MerchantRevenueSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MerchantWalletService merchantWalletService;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtTokenValidator jwtTokenValidator;

    @MockBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void deniesDirectAccessToRemovedInternalPaymentRevenueEndpointForAllRoles() throws Exception {
        String internalUrl = "/api/v1/payments/internal/merchants/4/revenue";

        mvc.perform(get(internalUrl)).andExpect(status().isForbidden());

        for (String role : List.of("DRIVER", "MERCHANT", "ADMIN")) {
            mvc.perform(get(internalUrl)
                            .header("X-User-Id", "99")
                            .header("X-User-Role", role)
                            .header("X-Internal-Secret", "rev-sec-test"))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(merchantWalletService);
    }

    @Test
    void enforcesMerchantRoleAndAuthenticatedOwnershipOnMerchantRevenueEndpoint() throws Exception {
        String url = "/api/v1/merchant/revenue";

        mvc.perform(get(url)).andExpect(status().isForbidden());
        mvc.perform(get(url).header("X-User-Id", "4").header("X-User-Role", "MERCHANT"))
                .andExpect(status().isForbidden());
        for (String role : List.of("DRIVER", "ADMIN")) {
            mvc.perform(get(url)
                            .header("X-User-Id", "4")
                            .header("X-User-Role", role)
                            .header("X-Internal-Secret", "rev-sec-test"))
                    .andExpect(status().isForbidden());
        }

        when(merchantWalletService.getRevenueSummary(4L, null, null, null, "DAY"))
                .thenReturn(MerchantRevenueResponse.builder()
                        .merchantId(4L)
                        .totalNetRevenue(new BigDecimal("100000"))
                        .build());

        mvc.perform(get(url)
                        .header("X-User-Id", "4")
                        .header("X-User-Role", "MERCHANT")
                        .header("X-Internal-Secret", "rev-sec-test")
                        .param("merchantId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merchantId").value(4))
                .andExpect(jsonPath("$.data.totalNetRevenue").value(100000));

        verify(merchantWalletService).getRevenueSummary(4L, null, null, null, "DAY");
        verifyNoMoreInteractions(merchantWalletService);
    }
}
