package com.smartparking.payment.api;
import com.smartparking.payment.api.controller.DriverWalletController;
import com.smartparking.payment.application.service.*;
import com.smartparking.payment.infrastructure.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DriverWalletController.class)
@Import({SecurityConfig.class, InternalAuthFilter.class})
@TestPropertySource(properties="internal.secret=wallet-test-only")
class DriverWalletControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DriverWalletLedger wallet;
    @MockBean DriverWalletPaymentService payments;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean JpaMetamodelMappingContext jpaMappingContext;
    @Test void readsOnlyAuthenticatedWalletIgnoringForgedOwner() throws Exception {
        when(payments.enabled()).thenReturn(true);
        when(wallet.balance(7L, true)).thenReturn(new DriverWalletLedger.Balance(new BigDecimal("8000"), BigDecimal.ZERO, "VND", true));
        mvc.perform(get("/api/v1/driver/wallet").header("X-User-Id","7").header("X-User-Role","DRIVER")
                .header("X-Internal-Secret","wallet-test-only").param("driverId","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availableAmount").value(8000));
        verify(wallet).balance(7L,true); verifyNoMoreInteractions(wallet);
    }
    @Test void anonymousUntrustedMerchantAndAdminCannotReadDriverWallet() throws Exception {
        mvc.perform(get("/api/v1/driver/wallet")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/driver/wallet").header("X-User-Id","7").header("X-User-Role","DRIVER")).andExpect(status().isForbidden());
        for (String role : new String[]{"MERCHANT","ADMIN"})
            mvc.perform(get("/api/v1/driver/wallet").header("X-User-Id","7").header("X-User-Role",role).header("X-Internal-Secret","wallet-test-only"))
                    .andExpect(status().isForbidden());
        verifyNoInteractions(wallet);
    }
    @Test void invalidPaymentInputIsRejectedBeforeDebitingWallet() throws Exception {
        for (String body : new String[]{"{}", "{\"bookingId\":-1}"})
            mvc.perform(post("/api/v1/payments/wallet").header("X-User-Id","7").header("X-User-Role","DRIVER")
                    .header("X-Internal-Secret","wallet-test-only").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(payments);
    }
}
