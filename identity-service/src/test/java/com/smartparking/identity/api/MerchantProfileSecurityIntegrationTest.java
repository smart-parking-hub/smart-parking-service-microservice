package com.smartparking.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.identity.application.dto.UpdateMerchantProfileRequest;
import com.smartparking.identity.domain.model.Merchant;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.MerchantRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import com.smartparking.identity.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MerchantProfileSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User merchantUser1;
    private Merchant merchantRecord1;
    private String merchantToken1;

    private User merchantUser2;
    private Merchant merchantRecord2;
    private String merchantToken2;

    private User driverUser;
    private String driverToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Create Merchant 1
        merchantUser1 = userRepository.save(User.builder()
                .phone("098" + (System.currentTimeMillis() % 10000000L))
                .fullName("Chu Bai Mot")
                .email("merchant1_" + suffix + "@smartparking.test")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(RoleEnum.MERCHANT)
                .status(UserStatusEnum.ACTIVE)
                .build());

        merchantRecord1 = merchantRepository.save(Merchant.builder()
                .user(merchantUser1)
                .citizenId("079" + suffix)
                .bankName("Techcombank")
                .bankAccountNum("190333444555")
                .accountHolderName(null)
                .walletBalance(BigDecimal.valueOf(100000))
                .build());

        merchantToken1 = createJwt(merchantUser1);

        // Create Merchant 2
        merchantUser2 = userRepository.save(User.builder()
                .phone("097" + (System.currentTimeMillis() % 10000000L))
                .fullName("Chu Bai Hai")
                .email("merchant2_" + suffix + "@smartparking.test")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(RoleEnum.MERCHANT)
                .status(UserStatusEnum.ACTIVE)
                .build());

        merchantRecord2 = merchantRepository.save(Merchant.builder()
                .user(merchantUser2)
                .citizenId("078" + suffix)
                .bankName("Vietcombank")
                .bankAccountNum("007100999888")
                .accountHolderName("ORIGINAL MERCHANT TWO")
                .walletBalance(BigDecimal.valueOf(200000))
                .build());

        merchantToken2 = createJwt(merchantUser2);

        // Create Driver User
        driverUser = userRepository.save(User.builder()
                .phone("096" + (System.currentTimeMillis() % 10000000L))
                .fullName("Tai Xe A")
                .email("driver_" + suffix + "@smartparking.test")
                .passwordHash(passwordEncoder.encode("secret123"))
                .role(RoleEnum.DRIVER)
                .status(UserStatusEnum.ACTIVE)
                .build());

        driverToken = createJwt(driverUser);
    }

    private String createJwt(User user) {
        CustomUserDetails principal = new CustomUserDetails(
                user.getId(),
                user.getPhone(),
                user.getPasswordHash(),
                user.getStatus(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateJwtToken(auth);
    }

    @Test
    @DisplayName("GET without token is rejected with 401 Unauthorized")
    void testGetWithoutTokenFails() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT without token is rejected with 401 Unauthorized")
    void testPutWithoutTokenFails() throws Exception {
        UpdateMerchantProfileRequest req = new UpdateMerchantProfileRequest();
        req.setBankName("Techcombank");

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET with non-merchant role (DRIVER) is rejected with 403 Forbidden")
    void testGetWithDriverRoleFails() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT with non-merchant role (DRIVER) is rejected with 403 Forbidden")
    void testPutWithDriverRoleFails() throws Exception {
        UpdateMerchantProfileRequest req = new UpdateMerchantProfileRequest();
        req.setBankName("Techcombank");

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET with valid MERCHANT token returns profile including nullable accountHolderName")
    void testGetWithMerchantTokenSuccess() throws Exception {
        var result = mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(merchantUser1.getId()))
                .andExpect(jsonPath("$.data.bankName").value("Techcombank"))
                .andExpect(jsonPath("$.data.bankAccountNum").value("190333444555"))
                .andExpect(jsonPath("$.data.maskedBankAccount").value("********4555"))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasKey("accountHolderName")))
                .andExpect(jsonPath("$.data.accountHolderName").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();

        // Also verify via Jackson JsonNode that the key explicitly exists and is null (not omitted)
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
        org.junit.jupiter.api.Assertions.assertTrue(dataNode.has("accountHolderName"),
                "JSON response must contain key 'accountHolderName'");
        org.junit.jupiter.api.Assertions.assertTrue(dataNode.get("accountHolderName").isNull(),
                "Key 'accountHolderName' must be explicitly null");
    }

    @Test
    @DisplayName("PUT with raw JSON containing whitespace trims before validation, preserves leading zeros, diacritics, and verifies HTTP round-trip")
    void testPutRawJsonWithWhitespaceTrimsAndPreservesRoundTrip() throws Exception {
        String rawJson = """
            {
                "bankName": "   Ngân hàng Ngoại thương Việt Nam   ",
                "bankAccountNum": "   0071001234567   ",
                "accountHolderName": "   Trần Văn Chủ Bãi   "
            }
            """;

        // PUT request with raw JSON
        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.bankName").value("Ngân hàng Ngoại thương Việt Nam"))
                .andExpect(jsonPath("$.data.bankAccountNum").value("0071001234567"))
                .andExpect(jsonPath("$.data.maskedBankAccount").value("*********4567"))
                .andExpect(jsonPath("$.data.accountHolderName").value("Trần Văn Chủ Bãi"));

        // Follow-up HTTP GET to verify persistence and round-trip via HTTP
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bankName").value("Ngân hàng Ngoại thương Việt Nam"))
                .andExpect(jsonPath("$.data.bankAccountNum").value("0071001234567"))
                .andExpect(jsonPath("$.data.accountHolderName").value("Trần Văn Chủ Bãi"));

        // Verify persisted entity in database
        Merchant updated = merchantRepository.findByUserId(merchantUser1.getId()).orElseThrow();
        assertEquals("Ngân hàng Ngoại thương Việt Nam", updated.getBankName());
        assertEquals("0071001234567", updated.getBankAccountNum());
        assertEquals("Trần Văn Chủ Bãi", updated.getAccountHolderName());
    }

    @Test
    @DisplayName("PUT legacy client with absent accountHolderName retains existing accountHolderName")
    void testPutLegacyClientAbsentFieldRetainsExisting() throws Exception {
        String rawJsonAbsent = """
            {
                "bankName": "MB Bank"
            }
            """;

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJsonAbsent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("MB Bank"))
                .andExpect(jsonPath("$.data.bankAccountNum").value("007100999888"))
                .andExpect(jsonPath("$.data.accountHolderName").value("ORIGINAL MERCHANT TWO"));

        // HTTP GET verification
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("MB Bank"))
                .andExpect(jsonPath("$.data.accountHolderName").value("ORIGINAL MERCHANT TWO"));

        Merchant updated = merchantRepository.findByUserId(merchantUser2.getId()).orElseThrow();
        assertEquals("MB Bank", updated.getBankName());
        assertEquals("ORIGINAL MERCHANT TWO", updated.getAccountHolderName());
    }

    @Test
    @DisplayName("PUT legacy client with explicit null accountHolderName retains existing accountHolderName (patch-like)")
    void testPutLegacyClientExplicitNullRetainsExisting() throws Exception {
        String rawJsonExplicitNull = """
            {
                "bankName": "BIDV",
                "accountHolderName": null
            }
            """;

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJsonExplicitNull))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("BIDV"))
                .andExpect(jsonPath("$.data.accountHolderName").value("ORIGINAL MERCHANT TWO"));

        // HTTP GET verification
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("BIDV"))
                .andExpect(jsonPath("$.data.accountHolderName").value("ORIGINAL MERCHANT TWO"));
    }

    @Test
    @DisplayName("PUT with whitespace-only bankName returns 400 Bad Request")
    void testPutWhitespaceBankNameReturns400() throws Exception {
        String rawJson = "{\"bankName\": \"    \"}";

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));
    }

    @Test
    @DisplayName("PUT with bankName exceeding 100 characters returns 400 Bad Request")
    void testPutBankNameExceeding100CharsReturns400() throws Exception {
        String rawJson = "{\"bankName\": \"" + "B".repeat(101) + "\"}";

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));
    }

    @Test
    @DisplayName("PUT with whitespace-only accountHolderName returns 400 Bad Request")
    void testPutWhitespaceAccountHolderNameReturns400() throws Exception {
        String rawJson = "{\"accountHolderName\": \"    \"}";

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));
    }

    @Test
    @DisplayName("PUT with accountHolderName exceeding 100 characters returns 400 Bad Request")
    void testPutAccountHolderNameExceeding100CharsReturns400() throws Exception {
        String rawJson = "{\"accountHolderName\": \"" + "A".repeat(101) + "\"}";

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));
    }

    @Test
    @DisplayName("PUT with whitespace-only bankAccountNum returns 400 Bad Request")
    void testPutWhitespaceBankAccountNumReturns400() throws Exception {
        String rawJson = "{\"bankAccountNum\": \"    \"}";

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));
    }

    @Test
    @DisplayName("PUT with bankAccountNum fewer than 6 digits returns 400 Bad Request")
    void testPutBankAccountNumLessThan6DigitsReturns400() throws Exception {
        String rawJson = "{\"bankAccountNum\": \"12345\"}";

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));
    }

    @Test
    @DisplayName("PUT with bankAccountNum greater than 30 digits returns 400 Bad Request")
    void testPutBankAccountNumGreaterThan30DigitsReturns400() throws Exception {
        String rawJson = "{\"bankAccountNum\": \"" + "1".repeat(31) + "\"}";

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));
    }

    @Test
    @DisplayName("PUT with bankAccountNum containing letters returns 400 Bad Request")
    void testPutBankAccountNumWithLettersReturns400() throws Exception {
        String rawJson = "{\"bankAccountNum\": \"007100ABC12\"}";

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_400"));
    }

    @Test
    @DisplayName("PUT atomicity: failure in any field rejects entire update without partial write")
    void testPutAtomicityNoPartialWrite() throws Exception {
        String originalBankName = merchantRecord1.getBankName();
        String originalAccountNum = merchantRecord1.getBankAccountNum();

        // Valid bankName with invalid bankAccountNum
        String rawJson = """
            {
                "bankName": "WILL_NOT_BE_SAVED_BANK",
                "bankAccountNum": "INVALID_CHARS_123"
            }
            """;

        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest());

        // Follow-up HTTP GET to confirm no partial update took place
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value(originalBankName))
                .andExpect(jsonPath("$.data.bankAccountNum").value(originalAccountNum));
    }

    @Test
    @DisplayName("PUT operates strictly on principal: forged userId / merchantId in body and query cannot affect other merchants")
    void testPrincipalIsolationWithForgedIds() throws Exception {
        String rawJsonWithForgedIds = """
            {
                "userId": 999999,
                "merchantId": 888888,
                "bankName": "Bank For Merchant One",
                "accountHolderName": "CHỦ BÃI MỘT HỢP LỆ"
            }
            """;

        // Merchant 1 sends forged query parameters and body targeting Merchant 2
        mockMvc.perform(put("/api/v1/merchant/profile?userId=" + merchantUser2.getId() + "&merchantId=" + merchantRecord2.getId())
                        .header("Authorization", "Bearer " + merchantToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJsonWithForgedIds))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(merchantUser1.getId()))
                .andExpect(jsonPath("$.data.bankName").value("Bank For Merchant One"))
                .andExpect(jsonPath("$.data.accountHolderName").value("CHỦ BÃI MỘT HỢP LỆ"));

        // Verify Merchant 2 remains completely untouched via HTTP GET
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", "Bearer " + merchantToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(merchantUser2.getId()))
                .andExpect(jsonPath("$.data.bankName").value("Vietcombank"))
                .andExpect(jsonPath("$.data.bankAccountNum").value("007100999888"))
                .andExpect(jsonPath("$.data.accountHolderName").value("ORIGINAL MERCHANT TWO"));

        // Verify Merchant 2 in repository
        Merchant m2 = merchantRepository.findByUserId(merchantUser2.getId()).orElseThrow();
        assertEquals("Vietcombank", m2.getBankName());
        assertEquals("007100999888", m2.getBankAccountNum());
        assertEquals("ORIGINAL MERCHANT TWO", m2.getAccountHolderName());
    }
}
