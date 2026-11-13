package com.smartparking.payment.api;

import com.smartparking.common.enums.WalletLedgerEntryTypeEnum;
import com.smartparking.payment.api.controller.MerchantLedgerController;
import com.smartparking.payment.application.service.MerchantLedgerQueryService;
import com.smartparking.payment.domain.model.WalletLedgerEntry;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import com.smartparking.payment.infrastructure.security.InternalAuthFilter;
import com.smartparking.payment.infrastructure.security.JwtTokenValidator;
import com.smartparking.payment.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MerchantLedgerController.class)
@Import({SecurityConfig.class, InternalAuthFilter.class, MerchantLedgerQueryService.class})
@TestPropertySource(properties = "internal.secret=ledger-test-only")
class MerchantLedgerControllerTest {
    private static final String URL = "/api/v1/merchant/revenue/ledger";
    @Autowired private MockMvc mvc;
    @MockBean private WalletLedgerEntryRepository repository;
    @MockBean private JwtTokenValidator jwtTokenValidator;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void scopesToAuthenticatedMerchantAndReturnsSignedAmountsWithPaging() throws Exception {
        var pageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "id"));
        var entry = WalletLedgerEntry.builder().id(81L).merchantId(7L)
                .entryType(WalletLedgerEntryTypeEnum.WITHDRAWAL_HOLD)
                .grossAmount(BigDecimal.ZERO).feePercent(BigDecimal.ZERO).feeAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("-50000")).createdAt(LocalDateTime.of(2026, 11, 14, 9, 30))
                .note("Giữ tiền rút").build();
        when(repository.findByMerchantIdOrderByCreatedAtDesc(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(entry), pageable, 21));
        mvc.perform(get(URL).header("X-User-Id", "7").header("X-User-Role", "MERCHANT")
                        .header("X-Internal-Secret", "ledger-test-only")
                        .param("merchantId", "999").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].netAmount").value(-50000))
                .andExpect(jsonPath("$.data.content[0].entryType").value("WITHDRAWAL_HOLD"))
                .andExpect(jsonPath("$.data.content[0].payment").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].merchantId").doesNotExist());
        verify(repository).findByMerchantIdOrderByCreatedAtDesc(7L, pageable);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyLedgerUsesDefaultPageAndSize() throws Exception {
        when(repository.findByMerchantIdOrderByCreatedAtDesc(eq(8L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        mvc.perform(get(URL).header("X-User-Id", "8").header("X-User-Role", "MERCHANT")
                        .header("X-Internal-Secret", "ledger-test-only"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        verify(repository).findByMerchantIdOrderByCreatedAtDesc(8L,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void rejectsAnonymousUntrustedHeadersAndOtherRoles() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        mvc.perform(get(URL).header("X-User-Id", "7").header("X-User-Role", "MERCHANT"))
                .andExpect(status().isForbidden());
        for (String role : List.of("DRIVER", "ADMIN")) {
            mvc.perform(get(URL).header("X-User-Id", "7").header("X-User-Role", role)
                            .header("X-Internal-Secret", "ledger-test-only"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidPagingBeforeReadingDatabase() throws Exception {
        for (String[] params : List.of(new String[]{"-1", "20"}, new String[]{"0", "0"},
                new String[]{"0", "101"}, new String[]{"oops", "20"})) {
            mvc.perform(get(URL).header("X-User-Id", "7").header("X-User-Role", "MERCHANT")
                            .header("X-Internal-Secret", "ledger-test-only")
                            .param("page", params[0]).param("size", params[1]))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }
}
