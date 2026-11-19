package com.smartparking.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.verification.*;
import com.smartparking.identity.application.service.verification.BusinessTaxRegistry;
import com.smartparking.identity.application.service.verification.MerchantVerificationAssessmentWorker;
import com.smartparking.identity.application.service.verification.OcrExtractionService;
import com.smartparking.identity.application.service.verification.VerificationCloudinaryCleanup;
import com.smartparking.identity.domain.model.Merchant;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.model.verification.OcrMethod;
import com.smartparking.identity.domain.model.verification.VerificationStatus;
import com.smartparking.identity.domain.repository.MerchantRepository;
import com.smartparking.identity.domain.repository.MerchantVerificationDossierRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import com.smartparking.identity.infrastructure.cloudinary.VerificationCloudinaryClient;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import com.smartparking.identity.infrastructure.security.JwtTokenProvider;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.verification.assessment-worker-enabled=false",
    "app.verification.cloudinary.writes-enabled=true",
    "app.verification.casso.minimum-interval=30s",
    "spring.datasource.url=jdbc:h2:mem:merchant_kyc_integration;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@AutoConfigureMockMvc
class MerchantVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantVerificationDossierRepository dossierRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MerchantVerificationAssessmentWorker assessmentWorker;

    @Autowired
    private VerificationCloudinaryCleanup cloudinaryCleanup;

    @MockBean
    private VerificationCloudinaryClient cloudinary;

    @MockBean
    private BusinessTaxRegistry taxRegistry;

    @SpyBean
    private OcrExtractionService ocrExtraction;

    private User merchantUser1;
    private Merchant merchantRecord1;
    private String merchantToken1;

    private User merchantUser2;
    private String merchantToken2;

    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS merchant_verification_cloudinary_cleanup (
                    public_id VARCHAR(150) PRIMARY KEY, next_attempt_at TIMESTAMP NOT NULL
                )
                """);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        merchantUser1 = userRepository.save(User.builder()
                .phone("091" + (System.currentTimeMillis() % 10000000L))
                .fullName("Nguyen Van Chu Bai 1")
                .email("m1_" + suffix + "@test.com")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(RoleEnum.MERCHANT)
                .status(UserStatusEnum.ACTIVE)
                .build());

        merchantRecord1 = merchantRepository.save(Merchant.builder()
                .user(merchantUser1)
                .citizenId("079" + suffix)
                .businessLicense("0101234567")
                .bankName("Vietcombank")
                .bankAccountNum("0071001234567")
                .walletBalance(BigDecimal.valueOf(500000))
                .build());

        merchantToken1 = createJwt(merchantUser1);

        merchantUser2 = userRepository.save(User.builder()
                .phone("092" + (System.currentTimeMillis() % 10000000L))
                .fullName("Tran Van Chu Bai 2")
                .email("m2_" + suffix + "@test.com")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(RoleEnum.MERCHANT)
                .status(UserStatusEnum.ACTIVE)
                .build());

        merchantRepository.save(Merchant.builder()
                .user(merchantUser2)
                .citizenId("078" + suffix)
                .bankName("MB Bank")
                .bankAccountNum("9988776655")
                .walletBalance(BigDecimal.ZERO)
                .build());

        merchantToken2 = createJwt(merchantUser2);

        adminUser = userRepository.save(User.builder()
                .phone("090" + (System.currentTimeMillis() % 10000000L))
                .fullName("Admin Quan Tri")
                .email("admin_" + suffix + "@test.com")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(RoleEnum.ADMIN)
                .status(UserStatusEnum.ACTIVE)
                .build());

        adminToken = createJwt(adminUser);
    }

    private String createJwt(User user) {
        CustomUserDetails principal = new CustomUserDetails(
                user.getId(),
                user.getPhone(),
                user.getPasswordHash(),
                user.getStatus(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateJwtToken(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0316794479", "0316794479-001"})
    void adminLooksUpOnlyTheCurrentSubmittedTaxCode(String taxCode) throws Exception {
        String merchantPath = "/api/v1/merchant/profile/verification";
        String adminPath = "/api/v1/admin/merchant-verifications";
        String merchantAuth = "Bearer " + merchantToken1;
        String adminAuth = "Bearer " + adminToken;
        var details = UpdateVerificationDetailsRequest.builder().draftRevision(1L)
                .businessRegistrationNumber(taxCode).legalName("CASSO BRANCH").build();
        var draft = mockMvc.perform(put(merchantPath + "/details").header("Authorization", merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isOk()).andReturn();
        long dossierId = objectMapper.readTree(draft.getResponse().getContentAsByteArray()).at("/data/id").asLong();
        String lookupPath = adminPath + "/" + dossierId + "/submissions/1/tax-lookup";
        String confirmation = "{\"confirmedBusinessTaxCode\":true}";

        mockMvc.perform(post(lookupPath).contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(lookupPath).header("Authorization", merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(lookupPath).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(status().isNotFound());

        uploadRegistration(merchantPath, merchantAuth, "registration.pdf", "CASSO BRANCH", "2");
        submit(merchantPath, merchantAuth, 3);
        jdbcTemplate.update("""
                UPDATE merchant_verification_submissions SET assessment_lease_until = ?
                WHERE dossier_id = ? AND submission_version = 1
                """, LocalDateTime.now().plusHours(1), dossierId);
        mockMvc.perform(post(lookupPath).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(lookupPath).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedBusinessTaxCode\":true,\"confirmedHouseholdTaxCode\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(adminPath + "/" + dossierId + "/submissions/2/tax-lookup").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(status().isConflict());
        verifyNoInteractions(taxRegistry);

        when(taxRegistry.isEnabled()).thenReturn(true);
        when(taxRegistry.lookup(taxCode)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return BusinessTaxRegistry.Result.found(new BusinessTaxRegistry.Business(
                    taxCode, "CASSO BRANCH", "NNT đang hoạt động", "https://www.gdt.gov.vn", false));
        });
        mockMvc.perform(post(lookupPath).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.data.dossierId").value(dossierId))
                .andExpect(jsonPath("$.data.submissionVersion").value(1))
                .andExpect(jsonPath("$.data.outcome").value("FOUND"))
                .andExpect(jsonPath("$.data.business.taxCode").value(taxCode))
                .andExpect(jsonPath("$.data.checkedAt").isString());
        mockMvc.perform(post(lookupPath).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.retryAt").isString());
        mockMvc.perform(get(adminPath + "/" + dossierId).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.assessment.status").value("PENDING"))
                .andExpect(jsonPath("$.data.history", hasSize(1)));
        mockMvc.perform(post(adminPath + "/" + dossierId + "/request-changes").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"submissionVersion":1,"items":[{"key":"BUSINESS_INFO","reason":"Kiểm tra tên đăng ký"}]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post(lookupPath).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(status().isConflict());
        verify(taxRegistry, times(1)).lookup(taxCode);
    }

    @Test
    void adminManuallyApprovesATwelveDigitRegistrationWithoutCallingCasso() throws Exception {
        String merchantPath = "/api/v1/merchant/profile/verification";
        String merchantAuth = "Bearer " + merchantToken1;
        String adminAuth = "Bearer " + adminToken;
        String registrationNumber = "012345678901";
        var details = UpdateVerificationDetailsRequest.builder().draftRevision(1L)
                .businessRegistrationNumber(registrationNumber).legalName("HO KINH DOANH MAU").build();
        var draft = mockMvc.perform(put(merchantPath + "/details").header("Authorization", merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isOk()).andReturn();
        long dossierId = objectMapper.readTree(draft.getResponse().getContentAsByteArray()).at("/data/id").asLong();
        String adminPath = "/api/v1/admin/merchant-verifications/" + dossierId;
        uploadRegistration(merchantPath, merchantAuth, "registration.pdf", "HO KINH DOANH MAU", "2");
        submit(merchantPath, merchantAuth, 3);

        for (String confirmation : List.of("{\"confirmedBusinessTaxCode\":true}", "{\"confirmedHouseholdTaxCode\":true}")) {
            mockMvc.perform(post(adminPath + "/submissions/1/tax-lookup").header("Authorization", adminAuth)
                            .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("đối chiếu trực tiếp trên giấy tờ")));
        }

        assessmentWorker.processNext();
        mockMvc.perform(post(adminPath + "/approve").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"submissionVersion":1,"documentsReviewed":true,"note":"Đã đối chiếu giấy tờ gốc."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.businessRegistrationNumber").value(registrationNumber));
        verifyNoInteractions(taxRegistry);
    }

    @Test
    @DisplayName("AC1, AC2, AC3, AC4, AC5, AC6: Complete lifecycle of merchant KYC verification")
    void completeVerificationLifecycle() throws Exception {
        // --- 1. AC1: Initial GET returns DRAFT, draftRevision=1 ---
        MvcResult getDraftRes = mockMvc.perform(get("/api/v1/merchant/profile/verification")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.draftRevision").value(1))
                .andExpect(jsonPath("$.data.submissionVersion").doesNotExist())
                .andReturn();

        assertThat(dossierRepository.findByMerchantId(merchantRecord1.getId())).isEmpty();

        // Check profile endpoint: existing merchant with businessLicense string is NOT verified
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.data.verificationLabel").value("Chờ xác thực"));

        // --- 2. AC1: Update details increments draftRevision ---
        UpdateVerificationDetailsRequest updateReq = UpdateVerificationDetailsRequest.builder()
                .draftRevision(1L)
                .businessRegistrationNumber("0101234567")
                .legalName("CONG TY TNHH SMART PARKING")
                .build();

        mockMvc.perform(put("/api/v1/merchant/profile/verification/details")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftRevision").value(2))
                .andExpect(jsonPath("$.data.businessRegistrationNumber").value("0101234567"))
                .andExpect(jsonPath("$.data.legalName").value("CONG TY TNHH SMART PARKING"));

        // --- 3. AC5: Update details with stale revision returns 409 CONFLICT ---
        UpdateVerificationDetailsRequest staleReq = UpdateVerificationDetailsRequest.builder()
                .draftRevision(1L) // outdated
                .businessRegistrationNumber("0101234567")
                .legalName("CONG TY TNHH SMART PARKING")
                .build();

        mockMvc.perform(put("/api/v1/merchant/profile/verification/details")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleReq)))
                .andExpect(status().isConflict());

        // --- 4. AC1: Submit without document fails with 400 ---
        SubmitVerificationRequest submitWithoutDoc = SubmitVerificationRequest.builder()
                .draftRevision(2L)
                .build();

        mockMvc.perform(post("/api/v1/merchant/profile/verification/submit")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitWithoutDoc)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Giấy chứng nhận đăng ký kinh doanh")));

        // --- 5. AC2: Upload valid synthetic Vietnamese PDF document ---
        byte[] pdfBytes = createSyntheticBusinessRegistrationPdf("0101234567", "CONG TY TNHH SMART PARKING");
        MockMultipartFile validPdfFile = new MockMultipartFile(
                "files", "gpkd.pdf", "application/pdf", pdfBytes);

        MvcResult uploadRes = mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/merchant/profile/verification/documents/BUSINESS_REGISTRATION")
                        .file(validPdfFile)
                        .param("draftRevision", "2")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftRevision").value(3))
                .andExpect(jsonPath("$.data.documents", hasSize(1)))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        String docId = objectMapper.readTree(uploadRes.getResponse().getContentAsString())
                .path("data").path("documents").get(0).path("id").asText();
        assertThat(docId).isNotEmpty();

        // --- 6. AC2: Download document content with security headers ---
        mockMvc.perform(get("/api/v1/merchant/profile/verification/documents/" + docId + "/content")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));

        // AC2: Other merchant gets the same response as for a missing document.
        mockMvc.perform(get("/api/v1/merchant/profile/verification/documents/" + docId + "/content")
                        .header("Authorization", "Bearer " + merchantToken2))
                .andExpect(status().isNotFound());

        // --- 7. AC1 & AC3: Submit dossier (DRAFT -> PENDING_REVIEW, submissionVersion 1) ---
        SubmitVerificationRequest submitReq = SubmitVerificationRequest.builder()
                .draftRevision(3L)
                .build();

        mockMvc.perform(post("/api/v1/merchant/profile/verification/submit")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.submissionVersion").value(1))
                .andExpect(jsonPath("$.data.draftRevision").value(4))
                .andExpect(jsonPath("$.data.assessment.status").value("PENDING"));

        Long dossierId = dossierRepository.findByMerchantId(merchantRecord1.getId()).orElseThrow().getId();
        assessmentWorker.processNext();

        // --- 8. AC4: Admin lists pending verifications and views detail ---
        mockMvc.perform(get("/api/v1/admin/merchant-verifications?status=PENDING_REVIEW")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + dossierId + ")]").exists());

        mockMvc.perform(get("/api/v1/admin/merchant-verifications/" + dossierId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.submissionVersion").value(1))
                .andExpect(jsonPath("$.data.assessment.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.assessment.checks", hasSize(4)))
                .andExpect(jsonPath("$.data.assessment.checks[?(@.code == 'REGISTRATION_NUMBER_MATCH')].result").value("PASS"));

        // Admin downloads document content
        mockMvc.perform(get("/api/v1/admin/merchant-verifications/" + dossierId + "/submissions/1/documents/" + docId + "/content")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));

        // Admin downloads with wrong submission version returns 404
        mockMvc.perform(get("/api/v1/admin/merchant-verifications/" + dossierId + "/submissions/99/documents/" + docId + "/content")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // --- 9. AC4: Admin requests changes (PENDING_REVIEW -> CHANGES_REQUESTED) ---
        AdminRequestChangesRequest reqChanges = AdminRequestChangesRequest.builder()
                .submissionVersion(1)
                .items(List.of(RequestedItemDto.builder()
                        .key("BUSINESS_REGISTRATION")
                        .reason("Vui lòng đính kèm bản quét màu có dấu giáp lai rõ hơn.")
                        .build()))
                .build();

        mockMvc.perform(post("/api/v1/admin/merchant-verifications/" + dossierId + "/request-changes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqChanges)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.requestedItems[0].key").value("BUSINESS_REGISTRATION"));

        mockMvc.perform(post("/api/v1/merchant/profile/verification/submit")
                        .header("Authorization", "Bearer " + merchantToken1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draftRevision\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("tài liệu mới")));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/merchant/profile/documents")
                        .param("businessLicense", "9999999999").header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/merchant/profile/verification")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(jsonPath("$.data.draftRevision").value(5))
                .andExpect(jsonPath("$.data.requestedItems[0].addressed").value(false))
                .andExpect(jsonPath("$.data.businessRegistrationNumber").value("0101234567"));

        // AC6: Outbox event recorded for CHANGES_REQUESTED
        Integer countChangesEvent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_event_outbox WHERE topic_name = 'merchant-verification.events'", Integer.class);
        assertThat(countChangesEvent).isGreaterThanOrEqualTo(1);

        // --- 10. AC4: Merchant cannot edit unrequested item (BUSINESS_INFO) ---
        UpdateVerificationDetailsRequest forbiddenEdit = UpdateVerificationDetailsRequest.builder()
                .draftRevision(5L)
                .businessRegistrationNumber("0101234567")
                .legalName("NEW NAME")
                .build();

        mockMvc.perform(put("/api/v1/merchant/profile/verification/details")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forbiddenEdit)))
                .andExpect(status().isConflict());

        // --- 11. AC4: Merchant uploads replacement document for requested item ---
        byte[] newPdfBytes = createSyntheticBusinessRegistrationPdf("0101234567", "CONG TY TNHH SMART PARKING NANG CAP");
        MockMultipartFile newPdfFile = new MockMultipartFile(
                "files", "gpkd_v2.pdf", "application/pdf", newPdfBytes);

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/merchant/profile/verification/documents/BUSINESS_REGISTRATION")
                        .file(newPdfFile)
                        .param("draftRevision", "5")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftRevision").value(6))
                .andExpect(jsonPath("$.data.requestedItems[0].addressed").value(true));

        mockMvc.perform(get("/api/v1/admin/merchant-verifications/" + dossierId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.submissionVersion").value(1))
                .andExpect(jsonPath("$.data.documents[0].id").value(docId))
                .andExpect(jsonPath("$.data.documents[0].originalName").value("gpkd.pdf"))
                .andExpect(jsonPath("$.data.editableItems", hasSize(0)));

        // Merchant resubmits (CHANGES_REQUESTED -> PENDING_REVIEW, version 2)
        SubmitVerificationRequest resubmitReq = SubmitVerificationRequest.builder()
                .draftRevision(6L)
                .build();

        mockMvc.perform(post("/api/v1/merchant/profile/verification/submit")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resubmitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.submissionVersion").value(2))
                .andExpect(jsonPath("$.data.draftRevision").value(7))
                .andExpect(jsonPath("$.data.assessment.status").value("PENDING"))
                .andExpect(jsonPath("$.data.history[?(@.action == 'CHANGES_REQUESTED')].items[0].key")
                        .value("BUSINESS_REGISTRATION"));

        assessmentWorker.processNext();

        // --- 12. AC5: Admin approves with outdated submission version 1 returns 409 ---
        AdminApproveVerificationRequest staleApproveReq = AdminApproveVerificationRequest.builder()
                .submissionVersion(1) // current is 2
                .documentsReviewed(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/merchant-verifications/" + dossierId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleApproveReq)))
                .andExpect(status().isConflict());

        // Admin approves without documentsReviewed returns 400
        AdminApproveVerificationRequest unreviewedReq = AdminApproveVerificationRequest.builder()
                .submissionVersion(2)
                .documentsReviewed(false)
                .build();

        mockMvc.perform(post("/api/v1/admin/merchant-verifications/" + dossierId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unreviewedReq)))
                .andExpect(status().isBadRequest());

        // --- 13. AC5: Admin approves version 2 successfully ---
        AdminApproveVerificationRequest validApproveReq = AdminApproveVerificationRequest.builder()
                .submissionVersion(2)
                .documentsReviewed(true)
                .note("Đã đối chiếu thông tin pháp lý hợp lệ.")
                .build();

        mockMvc.perform(post("/api/v1/admin/merchant-verifications/" + dossierId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApproveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.history[?(@.action == 'APPROVED')].note")
                        .value("Đã đối chiếu thông tin pháp lý hợp lệ."));

        mockMvc.perform(get("/api/v1/merchant/profile/verification")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(jsonPath("$.data.history[?(@.action == 'APPROVED')].note[?(@ != null)]").isEmpty())
                .andExpect(jsonPath("$.data.history[?(@.action == 'CHANGES_REQUESTED')].items[0].key")
                        .value("BUSINESS_REGISTRATION"));

        // AC5: Second admin approval attempt returns 409
        mockMvc.perform(post("/api/v1/admin/merchant-verifications/" + dossierId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApproveReq)))
                .andExpect(status().isConflict());

        // AC5: Any further mutation on VERIFIED dossier returns 409
        mockMvc.perform(post("/api/v1/merchant/profile/verification/submit")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resubmitReq)))
                .andExpect(status().isConflict());

        // AC1: Profile GET now returns VERIFIED and "Đã xác thực"
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verificationLabel").value("Đã xác thực"))
                .andExpect(jsonPath("$.data.businessLicense").value("0101234567"));

        // AC6: Outbox event recorded for APPROVED
        Integer countApprovedEvent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_event_outbox WHERE topic_name = 'merchant-verification.events'", Integer.class);
        assertThat(countApprovedEvent).isGreaterThanOrEqualTo(2);
    }

    @Test
    void delayedOcrDoesNotBlockAdminOrOverwriteNewVersionAndExpiredLeaseCanBeClaimed() throws Exception {
        String merchantPath = "/api/v1/merchant/profile/verification";
        String auth = "Bearer " + merchantToken1;
        String adminAuth = "Bearer " + adminToken;
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            started.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(ocrExtraction).extract(any(byte[].class), eq("application/pdf"), eq("delayed.pdf"));

        mockMvc.perform(put(merchantPath + "/details").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draftRevision\":1,\"businessRegistrationNumber\":\"0101234567\",\"legalName\":\"FIRST FIXTURE\"}"))
                .andExpect(status().isOk());
        uploadRegistration(merchantPath, auth, "delayed.pdf", "FIRST FIXTURE", "2");
        submit(merchantPath, auth, 3);
        long dossierId = dossierRepository.findByMerchantId(merchantRecord1.getId()).orElseThrow().getId();
        String adminPath = "/api/v1/admin/merchant-verifications/" + dossierId;

        try (var executor = Executors.newSingleThreadExecutor()) {
            var oldAssessment = executor.submit(assessmentWorker::processNext);
            try {
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                mockMvc.perform(get(adminPath).header("Authorization", adminAuth))
                        .andExpect(jsonPath("$.data.assessment.status").value("PENDING"));
                mockMvc.perform(post(adminPath + "/approve").header("Authorization", adminAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"submissionVersion\":1,\"documentsReviewed\":true}"))
                        .andExpect(status().isConflict());
                mockMvc.perform(post(adminPath + "/request-changes").header("Authorization", adminAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"submissionVersion\":1,\"items\":[{\"key\":\"BUSINESS_REGISTRATION\",\"reason\":\"New scan\"}]}"))
                        .andExpect(status().isOk());
                uploadRegistration(merchantPath, auth, "replacement.pdf", "SECOND FIXTURE", "5");
                submit(merchantPath, auth, 6);

                // Simulate a worker that claimed v2 and exited before writing its assessment.
                jdbcTemplate.update("""
                        UPDATE merchant_verification_submissions SET assessment_attempts=1, assessment_lease_until=?
                        WHERE dossier_id=? AND submission_version=2
                        """, java.time.LocalDateTime.now().minusSeconds(1), dossierId);
                assessmentWorker.processNext();
                mockMvc.perform(get(adminPath).header("Authorization", adminAuth))
                        .andExpect(jsonPath("$.data.submissionVersion").value(2))
                        .andExpect(jsonPath("$.data.assessment.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.data.assessment.checks[?(@.code == 'LEGAL_NAME_MATCH')].result").value("WARN"));
            } finally {
                release.countDown();
            }
            oldAssessment.get(10, TimeUnit.SECONDS);
        }

        mockMvc.perform(get(adminPath).header("Authorization", adminAuth))
                .andExpect(jsonPath("$.data.submissionVersion").value(2))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.assessment.checks[?(@.code == 'LEGAL_NAME_MATCH')].result").value("WARN"));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT CONCAT(assessment_status, ':', decision_action)
                FROM merchant_verification_submissions WHERE dossier_id=? AND submission_version=1
                """, String.class, dossierId)).isEqualTo("COMPLETED:CHANGES_REQUESTED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT assessment_attempts FROM merchant_verification_submissions WHERE dossier_id=? AND submission_version=2
                """, Integer.class, dossierId)).isEqualTo(2);
    }

    @Test
    void usesTheSelectedPrivateImageAndStartsNameCorrectionWithoutChangingTheSubmittedSnapshot() throws Exception {
        String merchantPath = "/api/v1/merchant/profile/verification";
        String merchantAuth = "Bearer " + merchantToken1;
        String adminAuth = "Bearer " + adminToken;
        byte[] png = createPng();
        when(cloudinary.download(anyString(), eq("png"), eq(png.length))).thenReturn(png);
        doAnswer(call -> OcrExtractionService.ExtractionResult.builder()
                .engineAvailable(true).readable(true).method(OcrMethod.IMAGE_OCR)
                .extractedRegistrationNumber("0101234567").extractedLegalName("CORRECT COMPANY")
                .build()).when(ocrExtraction).extract(any(), eq("image/png"), eq("selected.png"));

        mockMvc.perform(get(merchantPath).header(HttpHeaders.AUTHORIZATION, merchantAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.legalName").isEmpty());
        mockMvc.perform(put(merchantPath + "/details").header(HttpHeaders.AUTHORIZATION, merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"draftRevision":1,"businessRegistrationNumber":"0101234567","legalName":"DECLARED COMPANY"}
                                """))
                .andExpect(status().isOk());
        var upload = mockMvc.perform(multipart(HttpMethod.PUT, merchantPath + "/documents/BUSINESS_REGISTRATION")
                        .file(new MockMultipartFile("files", "first.pdf", "application/pdf",
                                createSyntheticBusinessRegistrationPdf("0101234567", "PDF COMPANY")))
                        .file(new MockMultipartFile("files", "selected.png", "image/png", png))
                        .param("draftRevision", "2").header(HttpHeaders.AUTHORIZATION, merchantAuth))
                .andExpect(status().isOk()).andReturn();
        String pngId = documentId(upload, "selected.png");
        var supporting = mockMvc.perform(multipart(HttpMethod.PUT, merchantPath + "/documents/SUPPORTING_DOCUMENT")
                        .file(new MockMultipartFile("files", "supporting.pdf", "application/pdf",
                                createSyntheticBusinessRegistrationPdf("0101234567", "SUPPORTING COMPANY")))
                        .param("draftRevision", "3").header(HttpHeaders.AUTHORIZATION, merchantAuth))
                .andExpect(status().isOk()).andReturn();
        String supportingId = documentId(supporting, "supporting.pdf");
        String publicId = "smartparking/merchant-kyc/" + pngId;
        verify(cloudinary).upload(eq(publicId), eq("png"), eq(png));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cloudinary_public_id FROM merchant_verification_documents WHERE id=?", String.class, pngId))
                .isEqualTo(publicId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_verification_document_bytes WHERE document_id=?", Integer.class, pngId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_verification_cloudinary_cleanup WHERE public_id=?", Integer.class, publicId))
                .isZero();

        mockMvc.perform(get(merchantPath + "/documents/" + pngId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken2))
                .andExpect(status().isNotFound());
        verify(cloudinary, never()).download(anyString(), anyString(), anyInt());
        for (String invalid : new String[] {null, "other-draft-file", supportingId}) {
            mockMvc.perform(post(merchantPath + "/submit").header(HttpHeaders.AUTHORIZATION, merchantAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(SubmitVerificationRequest.builder()
                                    .draftRevision(4L).ocrDocumentId(invalid).build())))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(merchantPath + "/submit").header(HttpHeaders.AUTHORIZATION, merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(SubmitVerificationRequest.builder()
                                .draftRevision(4L).ocrDocumentId(pngId).build())))
                .andExpect(status().isOk());
        assessmentWorker.processNext();
        long dossierId = dossierRepository.findByMerchantId(merchantRecord1.getId()).orElseThrow().getId();
        String adminPath = "/api/v1/admin/merchant-verifications/" + dossierId;
        mockMvc.perform(get(adminPath).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assessment.source.documentId").value(pngId))
                .andExpect(jsonPath("$.data.assessment.source.originalName").value("selected.png"))
                .andExpect(jsonPath("$.data.assessment.source.method").value("IMAGE_OCR"))
                .andExpect(jsonPath("$.data.assessment.source.submissionVersion").value(1))
                .andExpect(jsonPath("$.data.assessment.checks[?(@.code == 'LEGAL_NAME_MATCH')].result").value("WARN"));
        mockMvc.perform(get(adminPath + "/submissions/2/documents/" + pngId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound());
        verify(cloudinary).download(publicId, "png", png.length);
        mockMvc.perform(get(adminPath + "/submissions/1/documents/" + pngId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk()).andExpect(content().bytes(png))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));

        mockMvc.perform(post(adminPath + "/request-changes").header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"submissionVersion":1,"resetLegalName":true,"items":[{"key":"BUSINESS_INFO",
                                "reason":"DECLARED COMPANY differs from CORRECT COMPANY"}]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.legalName").value("DECLARED COMPANY"));
        mockMvc.perform(get(merchantPath).header(HttpHeaders.AUTHORIZATION, merchantAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.legalName").isEmpty())
                .andExpect(jsonPath("$.data.requestedItems[0].addressed").value(false));
        mockMvc.perform(post(merchantPath + "/submit").header(HttpHeaders.AUTHORIZATION, merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draftRevision\":6}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(merchantPath + "/details").header(HttpHeaders.AUTHORIZATION, merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"draftRevision":6,"businessRegistrationNumber":"0101234567","legalName":"CORRECT COMPANY"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post(merchantPath + "/submit").header(HttpHeaders.AUTHORIZATION, merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(SubmitVerificationRequest.builder()
                                .draftRevision(7L).ocrDocumentId(pngId).build())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.submissionVersion").value(2))
                .andExpect(jsonPath("$.data.legalName").value("CORRECT COMPANY"));
        assessmentWorker.processNext();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT legal_name FROM merchant_verification_submissions WHERE dossier_id=? AND submission_version=1
                """, String.class, dossierId)).isEqualTo("DECLARED COMPANY");
        mockMvc.perform(get(adminPath + "/submissions/1/documents/" + pngId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk()).andExpect(content().bytes(png));
        verify(cloudinary, never()).destroy(publicId);
    }

    @Test
    void failedReplacementRollsBackTheGroupAndQueuesEveryAttemptedImageForCleanup() throws Exception {
        String merchantPath = "/api/v1/merchant/profile/verification";
        String auth = "Bearer " + merchantToken1;
        byte[] png = createPng();
        mockMvc.perform(multipart("/api/v1/merchant/profile/documents")
                        .file(new MockMultipartFile("file", "original.png", "image/png", png))
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        String retainedId = jdbcTemplate.queryForObject("""
                SELECT d.cloudinary_public_id FROM merchant_verification_documents d
                JOIN merchant_verification_dossiers v ON v.id=d.dossier_id WHERE v.merchant_id=?
                """, String.class, merchantRecord1.getId());
        assertThat(retainedId).startsWith("smartparking/merchant-kyc/");
        when(cloudinary.download(eq(retainedId), eq("png"), eq(png.length))).thenReturn(png);
        doNothing().doThrow(new AppException(ErrorCode.INTERNAL_SERVER_ERROR))
                .when(cloudinary).upload(anyString(), eq("png"), any());
        mockMvc.perform(multipart(HttpMethod.PUT, merchantPath + "/documents/BUSINESS_REGISTRATION")
                        .file(new MockMultipartFile("files", "first.png", "image/png", png))
                        .file(new MockMultipartFile("files", "second.png", "image/png", png))
                        .param("draftRevision", "2").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isInternalServerError());
        mockMvc.perform(get(merchantPath).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.draftRevision").value(2))
                .andExpect(jsonPath("$.data.legalName").isEmpty())
                .andExpect(jsonPath("$.data.documents", hasSize(1)))
                .andExpect(jsonPath("$.data.documents[0].originalName").value("original.png"));
        var pending = jdbcTemplate.queryForList(
                "SELECT public_id FROM merchant_verification_cloudinary_cleanup", String.class);
        assertThat(pending).hasSize(2).doesNotContain(retainedId);
        when(cloudinary.isConfigured()).thenReturn(true);
        jdbcTemplate.update("UPDATE merchant_verification_cloudinary_cleanup SET next_attempt_at=CURRENT_TIMESTAMP");
        cloudinaryCleanup.cleanNext();
        cloudinaryCleanup.cleanNext();
        pending.forEach(id -> verify(cloudinary).destroy(id));
        verify(cloudinary, never()).destroy(retainedId);
        assertThat(jdbcTemplate.queryForList("SELECT public_id FROM merchant_verification_cloudinary_cleanup"))
                .isEmpty();
    }

    private byte[] createPng() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private String documentId(MvcResult upload, String filename) throws IOException {
        for (var doc : objectMapper.readTree(upload.getResponse().getContentAsByteArray()).at("/data/documents")) {
            if (filename.equals(doc.path("originalName").asText())) {
                return doc.path("id").asText();
            }
        }
        throw new AssertionError("Missing uploaded document " + filename);
    }

    private void uploadRegistration(String merchantPath, String auth, String filename, String name, String revision)
            throws Exception {
        var file = new MockMultipartFile("files", filename, "application/pdf",
                createSyntheticBusinessRegistrationPdf("0101234567", name));
        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, merchantPath + "/documents/BUSINESS_REGISTRATION")
                        .file(file).param("draftRevision", revision).header("Authorization", auth))
                .andExpect(status().isOk());
    }

    private void submit(String merchantPath, String auth, int revision) throws Exception {
        mockMvc.perform(post(merchantPath + "/submit").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SubmitVerificationRequest.builder()
                                .draftRevision((long) revision).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assessment.status").value("PENDING"));
    }

    private byte[] createSyntheticBusinessRegistrationPdf(String regNo, String companyName) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("CONG HOA XA HOI CHU NGHIA VIET NAM");
                cs.newLineAtOffset(0, -20);
                cs.showText("GIAY CHUNG NHAN DANG KY DOANH NGHIEP");
                cs.newLineAtOffset(0, -30);

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("Ma so doanh nghiep: " + regNo);
                cs.newLineAtOffset(0, -20);
                cs.showText("Ten cong ty: " + companyName);
                cs.newLineAtOffset(0, -20);
                cs.showText("Dia chi tru so chinh: 123 Nguyen Trai, Ha Noi");
                cs.endText();
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
