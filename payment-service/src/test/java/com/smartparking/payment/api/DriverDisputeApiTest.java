package com.smartparking.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.security.SecurityConstants;
import com.smartparking.payment.domain.model.Dispute;
import com.smartparking.payment.domain.model.DisputeEvidenceFile;
import com.smartparking.payment.domain.repository.DisputeEvidenceFileRepository;
import com.smartparking.payment.domain.repository.DisputeHistoryRepository;
import com.smartparking.payment.domain.repository.DisputeRepository;
import com.smartparking.payment.infrastructure.client.DisputeBookingClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DriverDisputeApiTest {

    @TempDir
    static Path tempEvidenceDir;

    @DynamicPropertySource
    static void configureEvidenceDir(DynamicPropertyRegistry registry) {
        registry.add("app.storage.dispute-evidence-dir", () -> tempEvidenceDir.toAbsolutePath().toString());
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Long DRIVER_ID = 200L;
    private static final Long OTHER_DRIVER_ID = 201L;
    private static final Long MERCHANT_ID = 300L;
    private static final Long OTHER_MERCHANT_ID = 301L;
    private static final Long ADMIN_ID = 1L;
    private static final Long PARKING_LOT_ID = 88L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private DisputeHistoryRepository disputeHistoryRepository;

    @Autowired
    private DisputeEvidenceFileRepository disputeEvidenceFileRepository;

    @MockBean
    private DisputeBookingClient disputeBookingClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${jwt.secret:smartparkingsecretkeymustbeverylongandsecure2026version}")
    private String jwtSecret;

    @BeforeEach
    void cleanDb() {
        disputeEvidenceFileRepository.deleteAll();
        disputeHistoryRepository.deleteAll();
        disputeRepository.deleteAll();
    }

    @Test
    @DisplayName("Security boundaries reject anonymous, spoofed headers, and wrong roles while allowing direct Bearer JWT")
    void securityBoundariesAndJwtAuthentication() throws Exception {
        String validBody = """
                {
                  "bookingId": 1001,
                  "reason": "Thu phí sai biểu giá"
                }
                """;

        // Anonymous calls blocked
        mockMvc.perform(post("/api/v1/disputes").contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/disputes/me"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/disputes/me/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/disputes/1/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"ok\"}"))
                .andExpect(status().isForbidden());

        // Spoofed X-User-Id without valid X-Internal-Secret rejected
        mockMvc.perform(get("/api/v1/disputes/me")
                        .header(SecurityConstants.HEADER_USER_ID, DRIVER_ID)
                        .header(SecurityConstants.HEADER_USER_ROLE, "DRIVER")
                        .header(SecurityConstants.HEADER_INTERNAL_SECRET, "forged-secret"))
                .andExpect(status().isForbidden());

        // Wrong roles blocked
        mockMvc.perform(withGateway(post("/api/v1/disputes"), MERCHANT_ID, "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), MERCHANT_ID, "MERCHANT"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(get("/api/v1/disputes/me/1"), MERCHANT_ID, "MERCHANT"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), ADMIN_ID, "ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(post("/api/v1/disputes/1/responses"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"not merchant\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(post("/api/v1/disputes/1/responses"), ADMIN_ID, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"admin\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(get("/api/v1/disputes/1"), ADMIN_ID, "ADMIN"))
                .andExpect(status().isForbidden());

        // Direct JWT Bearer auth works without gateway X-User-* headers
        when(disputeBookingClient.verifyBookingOwnership(1001L, DRIVER_ID)).thenReturn(
                new DisputeBookingClient.VerifiedBooking(1001L, DRIVER_ID, MERCHANT_ID, PARKING_LOT_ID));
        String driverJwt = signJwt(DRIVER_ID, "DRIVER");

        mockMvc.perform(post("/api/v1/disputes")
                        .header(SecurityConstants.AUTH_HEADER, "Bearer " + driverJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.merchantId").value(MERCHANT_ID))
                .andExpect(jsonPath("$.data.parkingLotId").value(PARKING_LOT_ID));
    }

    @Test
    @DisplayName("POST /disputes validates inputs, verifies booking ownership, ignores client merchantId, and rejects duplicate booking")
    void createDisputeValidationOwnershipAndDuplicates() throws Exception {
        when(disputeBookingClient.verifyBookingOwnership(2001L, DRIVER_ID)).thenReturn(
                new DisputeBookingClient.VerifiedBooking(2001L, DRIVER_ID, MERCHANT_ID, PARKING_LOT_ID));
        when(disputeBookingClient.verifyBookingOwnership(4040L, DRIVER_ID)).thenThrow(
                new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy lượt đặt chỗ"));
        when(disputeBookingClient.verifyBookingOwnership(4030L, DRIVER_ID)).thenThrow(
                new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền khiếu nại lượt đặt chỗ này"));

        String validRequest = """
                {
                  "bookingId": 2001,
                  "merchantId": 99999,
                  "parkingLotId": 88,
                  "reason": "  Barie kẹt tính sai giờ  ",
                  "description": "Kẹt 15 phút lúc check-out",
                  "evidenceUrls": [
                    "https://cdn.example.com/img1.jpg",
                    "http://cdn.example.com:8080/img2.png?v=1"
                  ]
                }
                """;

        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookingId").value(2001))
                .andExpect(jsonPath("$.data.merchantId").value(MERCHANT_ID))
                .andExpect(jsonPath("$.data.parkingLotId").value(PARKING_LOT_ID))
                .andExpect(jsonPath("$.data.reason").value("Barie kẹt tính sai giờ"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.evidenceUrls", hasSize(2)));

        // Duplicate while OPEN -> 409 CONFLICT
        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": 2001, \"reason\": \"Gửi lại\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));

        // Resolve dispute and verify duplicate while RESOLVED is STILL 409 CONFLICT
        Long createdId = disputeRepository.findByBookingId(2001L).orElseThrow().getId();
        mockMvc.perform(withGateway(put("/api/v1/disputes/{id}/resolve", createdId), ADMIN_ID, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\": \"Đã xử lý\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": 2001, \"reason\": \"Khiếu nại lại sau khi đóng\"}"))
                .andExpect(status().isConflict());

        // Mismatched parkingLotId -> 400 BAD_REQUEST
        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": 2001, \"parkingLotId\": 999, \"reason\": \"Sai bãi\"}"))
                .andExpect(status().isBadRequest());

        // Missing or unowned booking from Booking service
        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": 4040, \"reason\": \"Không tồn tại\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_NOT_FOUND.getCode()));

        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": 4030, \"reason\": \"Booking người khác\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        // Invalid DTO validations -> 400
        String tooLongReason = "a".repeat(201);
        List<String> badPayloads = List.of(
                "{\"reason\": \"Thiếu booking\"}",
                "{\"bookingId\": 2002, \"reason\": \"   \"}",
                "{\"bookingId\": 2002, \"reason\": \"" + tooLongReason + "\"}",
                "{\"bookingId\": 2002, \"reason\": \"ok\", \"evidenceUrls\": [\"https://a.com/1\",\"https://a.com/2\",\"https://a.com/3\",\"https://a.com/4\",\"https://a.com/5\",\"https://a.com/6\"]}",
                "{\"bookingId\": 2002, \"reason\": \"ok\", \"evidenceUrls\": [\"javascript:alert(1)\"]}",
                "{\"bookingId\": 2002, \"reason\": \"ok\", \"evidenceUrls\": [\"/relative/img.png\"]}",
                "{\"bookingId\": 2002, \"reason\": \"ok\", \"evidenceUrls\": [\"https://\"]}",
                "{\"bookingId\": 2002, \"reason\": \"ok\", \"evidenceUrls\": [\"https://exa mple.com/a.png\"]}",
                "{\"bookingId\": 2002, \"reason\": \"ok\", \"evidenceUrls\": [\"\"]}",
                "{\"bookingId\": 2002, \"reason\": \"ok\", \"evidenceUrls\": [null]}",
                "{\"bookingId\": 2002, \"reason\": \"ok\", \"evidenceUrls\": [\"ftp://cdn.example.com/a.png\"]}"
        );
        for (String payload : badPayloads) {
            mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        }

        // Malformed JSON -> 400 without exposing internals
        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"not-a-long\", \"reason\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu JSON không hợp lệ"));
    }

    @Test
    @DisplayName("GET /disputes/me filters by OPEN/IN_REVIEW/RESOLVED/REJECTED, clamps paging, ignores sort, and rejects bad query params")
    void getMyDisputesFilteringPagingAndValidation() throws Exception {
        Dispute openDispute = saveDispute(3001L, DRIVER_ID, DisputeStatusEnum.PENDING, LocalDateTime.now().minusMinutes(40));
        Dispute inReviewDispute = saveDispute(3002L, DRIVER_ID, DisputeStatusEnum.PENDING, LocalDateTime.now().minusMinutes(30));
        Dispute resolvedDispute = saveDispute(3003L, DRIVER_ID, DisputeStatusEnum.RESOLVED, LocalDateTime.now().minusMinutes(20));
        Dispute rejectedDispute = saveDispute(3004L, DRIVER_ID, DisputeStatusEnum.REJECTED, LocalDateTime.now().minusMinutes(10));
        saveDispute(3005L, OTHER_DRIVER_ID, DisputeStatusEnum.PENDING, LocalDateTime.now());

        // Move 3002 to IN_REVIEW via merchant response
        mockMvc.perform(withGateway(post("/api/v1/disputes/{id}/responses", inReviewDispute.getId()), MERCHANT_ID, "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Đang kiểm tra cảm biến barie\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resultingStatus").value("IN_REVIEW"));

        // Default list: only DRIVER_ID's 4 disputes, newest first, sort param ignored
        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.currentPage").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(4)))
                .andExpect(jsonPath("$.data.content[0].id").value(rejectedDispute.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.content[1].id").value(resolvedDispute.getId()))
                .andExpect(jsonPath("$.data.content[1].status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.content[2].id").value(inReviewDispute.getId()))
                .andExpect(jsonPath("$.data.content[2].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.content[3].id").value(openDispute.getId()))
                .andExpect(jsonPath("$.data.content[3].status").value("OPEN"));

        // Out-of-range numeric bounds sanitized safely
        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER")
                        .param("page", "-5")
                        .param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPage").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(4)));

        // Individual status filters
        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(openDispute.getId()));

        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER").param("status", "IN_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(inReviewDispute.getId()));

        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(resolvedDispute.getId()));

        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER").param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(rejectedDispute.getId()));

        // Strict status parameter rejection & type mismatch rejection -> 400
        for (String invalidStatus : List.of("", "open", "PENDING", "UNKNOWN")) {
            mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER").param("status", invalidStatus))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        }
        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER").param("page", "abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withGateway(get("/api/v1/disputes/me"), DRIVER_ID, "DRIVER").param("size", "xyz"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /disputes/me/{id}, merchant responses, Admin resolve timeline, and legacy IDOR protection work end-to-end")
    void detailTimelineMerchantResponseAndLegacyIdor() throws Exception {
        when(disputeBookingClient.verifyBookingOwnership(4001L, DRIVER_ID)).thenReturn(
                new DisputeBookingClient.VerifiedBooking(4001L, DRIVER_ID, MERCHANT_ID, PARKING_LOT_ID));

        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": 4001,
                                  "reason": "Trừ tiền hai lần",
                                  "evidenceUrls": ["https://cdn.example.com/payos.png"]
                                }
                                """))
                .andExpect(status().isCreated());

        Long disputeId = disputeRepository.findByBookingId(4001L).orElseThrow().getId();

        // Ownership & missing ID on /me/{id} -> 404 without existence leak
        mockMvc.perform(withGateway(get("/api/v1/disputes/me/{id}", disputeId), OTHER_DRIVER_ID, "DRIVER"))
                .andExpect(status().isNotFound());
        mockMvc.perform(withGateway(get("/api/v1/disputes/me/999999"), DRIVER_ID, "DRIVER"))
                .andExpect(status().isNotFound());
        mockMvc.perform(withGateway(get("/api/v1/disputes/me/not-an-id"), DRIVER_ID, "DRIVER"))
                .andExpect(status().isBadRequest());

        // Other merchant & blank message rejected
        mockMvc.perform(withGateway(post("/api/v1/disputes/{id}/responses", disputeId), OTHER_MERCHANT_ID, "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Không thuộc bãi tôi\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(post("/api/v1/disputes/{id}/responses", disputeId), MERCHANT_ID, "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withGateway(post("/api/v1/disputes/999999/responses"), MERCHANT_ID, "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Thiếu dispute\"}"))
                .andExpect(status().isNotFound());

        // Legacy IDOR check: Driver role cannot access using userId == merchantId, Merchant role cannot access using userId == driverId
        mockMvc.perform(withGateway(get("/api/v1/disputes/{id}", disputeId), MERCHANT_ID, "DRIVER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(get("/api/v1/disputes/{id}", disputeId), DRIVER_ID, "MERCHANT"))
                .andExpect(status().isForbidden());

        // Owning merchant responds -> legacy endpoints keep PENDING, driver detail transitions to IN_REVIEW
        mockMvc.perform(withGateway(post("/api/v1/disputes/{id}/responses", disputeId), MERCHANT_ID, "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Đã đối soát cổng PayOS với tài xế\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(withGateway(get("/api/v1/disputes/merchant"), MERCHANT_ID, "MERCHANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
        mockMvc.perform(withGateway(get("/api/v1/disputes/{id}", disputeId), MERCHANT_ID, "MERCHANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // Admin resolves atomically with timeline entry
        mockMvc.perform(withGateway(put("/api/v1/disputes/{id}/resolve", disputeId), ADMIN_ID, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\": \"Xác nhận hoàn 50.000đ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        // Closed dispute rejects late merchant response with 409 CONFLICT
        mockMvc.perform(withGateway(post("/api/v1/disputes/{id}/responses", disputeId), MERCHANT_ID, "MERCHANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Phản hồi muộn\"}"))
                .andExpect(status().isConflict());

        // Driver detail returns full chronological timeline without exposing actorUserId
        String detailJson = mockMvc.perform(withGateway(get("/api/v1/disputes/me/{id}", disputeId), DRIVER_ID, "DRIVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.evidenceUrls[0]").value("https://cdn.example.com/payos.png"))
                .andExpect(jsonPath("$.data.timeline", hasSize(2)))
                .andExpect(jsonPath("$.data.timeline[0].eventType").value("MERCHANT_RESPONSE"))
                .andExpect(jsonPath("$.data.timeline[0].actorRole").value("MERCHANT"))
                .andExpect(jsonPath("$.data.timeline[0].resultingStatus").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.timeline[1].eventType").value("ADMIN_RESOLUTION"))
                .andExpect(jsonPath("$.data.timeline[1].actorRole").value("ADMIN"))
                .andExpect(jsonPath("$.data.timeline[1].resultingStatus").value("RESOLVED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(detailJson.contains("actorUserId"), "Public timeline must not serialize internal actorUserId");
    }

    @Test
    @DisplayName("POST /disputes/evidence validates images, links uploaded evidence to dispute atomically, and enforces role/ownership on GET content")
    void uploadAndServeDisputeEvidenceEndToEnd() throws Exception {
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03, 0x04
        };
        byte[] jpegBytes = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };
        byte[] webpBytes = new byte[]{
                'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
        };

        MockMultipartFile pngFile = new MockMultipartFile("files", "bien-lai.png", "image/png", pngBytes);
        MockMultipartFile webpFile = new MockMultipartFile("files", "hien-truong.webp", "image/webp", webpBytes);

        // Anonymous, MERCHANT, ADMIN cannot upload driver evidence
        mockMvc.perform(multipart("/api/v1/disputes/evidence").file(pngFile))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(multipart("/api/v1/disputes/evidence").file(pngFile), MERCHANT_ID, "MERCHANT"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(multipart("/api/v1/disputes/evidence").file(pngFile), ADMIN_ID, "ADMIN"))
                .andExpect(status().isForbidden());

        // Magic-byte spoofing (.png containing JPEG header), bad extension (.pdf), and empty file rejected -> 400
        MockMultipartFile spoofedPng = new MockMultipartFile("files", "fake.png", "image/png", jpegBytes);
        mockMvc.perform(withGateway(multipart("/api/v1/disputes/evidence").file(spoofedPng), DRIVER_ID, "DRIVER"))
                .andExpect(status().isBadRequest());

        MockMultipartFile pdfFile = new MockMultipartFile("files", "doc.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(withGateway(multipart("/api/v1/disputes/evidence").file(pdfFile), DRIVER_ID, "DRIVER"))
                .andExpect(status().isBadRequest());

        MockMultipartFile emptyPng = new MockMultipartFile("files", "empty.png", "image/png", new byte[0]);
        mockMvc.perform(withGateway(multipart("/api/v1/disputes/evidence").file(emptyPng), DRIVER_ID, "DRIVER"))
                .andExpect(status().isBadRequest());

        // Upload 2 valid images as DRIVER_ID
        String uploadJson = mockMvc.perform(withGateway(multipart("/api/v1/disputes/evidence")
                        .file(pngFile)
                        .file(webpFile), DRIVER_ID, "DRIVER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].originalFileName").value("bien-lai.png"))
                .andExpect(jsonPath("$.data[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.data[0].contentUrl", org.hamcrest.Matchers.startsWith("/api/v1/disputes/evidence/")))
                .andExpect(jsonPath("$.data[0].url", org.hamcrest.Matchers.startsWith("http")))
                .andExpect(jsonPath("$.data[1].contentType").value("image/webp"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode uploadedArray = OBJECT_MAPPER.readTree(uploadJson).get("data");
        String pngFileName = uploadedArray.get(0).get("fileName").asText();
        String pngUrl = uploadedArray.get(0).get("url").asText();
        String webpUrl = uploadedArray.get(1).get("url").asText();

        // While unattached: owner driver and admin can fetch content; unlinked merchant & other driver cannot
        mockMvc.perform(get("/api/v1/disputes/evidence/{fileName}", pngFileName))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(get("/api/v1/disputes/evidence/{fileName}", pngFileName), OTHER_DRIVER_ID, "DRIVER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(get("/api/v1/disputes/evidence/{fileName}", pngFileName), MERCHANT_ID, "MERCHANT"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withGateway(get("/api/v1/disputes/evidence/{fileName}", pngFileName), DRIVER_ID, "DRIVER"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(pngBytes));

        // Other driver cannot attach DRIVER_ID's uploaded file -> 403 FORBIDDEN
        when(disputeBookingClient.verifyBookingOwnership(5001L, OTHER_DRIVER_ID)).thenReturn(
                new DisputeBookingClient.VerifiedBooking(5001L, OTHER_DRIVER_ID, MERCHANT_ID, PARKING_LOT_ID));
        mockMvc.perform(withGateway(post("/api/v1/disputes"), OTHER_DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(java.util.Map.of(
                                "bookingId", 5001L,
                                "reason", "Attempt stolen evidence",
                                "evidenceUrls", List.of(pngUrl)))))
                .andExpect(status().isForbidden());

        // Owner driver creates dispute attaching uploaded URLs -> 201 CREATED & file rows linked to dispute
        when(disputeBookingClient.verifyBookingOwnership(5002L, DRIVER_ID)).thenReturn(
                new DisputeBookingClient.VerifiedBooking(5002L, DRIVER_ID, MERCHANT_ID, PARKING_LOT_ID));
        String createdJson = mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(java.util.Map.of(
                                "bookingId", 5002L,
                                "reason", "Thu thêm tiền ngoài cổng",
                                "description", "Có biên lai đính kèm",
                                "evidenceUrls", List.of(pngUrl, webpUrl)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evidenceUrls", hasSize(2)))
                .andExpect(jsonPath("$.data.evidenceUrls[0]").value(pngUrl))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long createdDisputeId = OBJECT_MAPPER.readTree(createdJson).get("data").get("id").asLong();

        DisputeEvidenceFile linkedRecord = disputeEvidenceFileRepository.findByStoredFileName(pngFileName).orElseThrow();
        assertEquals(createdDisputeId, linkedRecord.getDisputeId());

        // Reusing already-attached evidence URL on another booking -> 409 CONFLICT
        when(disputeBookingClient.verifyBookingOwnership(5003L, DRIVER_ID)).thenReturn(
                new DisputeBookingClient.VerifiedBooking(5003L, DRIVER_ID, MERCHANT_ID, PARKING_LOT_ID));
        mockMvc.perform(withGateway(post("/api/v1/disputes"), DRIVER_ID, "DRIVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(java.util.Map.of(
                                "bookingId", 5003L,
                                "reason", "Reuse evidence",
                                "evidenceUrls", List.of(pngUrl)))))
                .andExpect(status().isConflict());

        // Once attached: linked MERCHANT_ID and ADMIN_ID can fetch image bytes, OTHER_MERCHANT_ID still 403
        mockMvc.perform(withGateway(get("/api/v1/disputes/evidence/{fileName}", pngFileName), MERCHANT_ID, "MERCHANT"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(pngBytes));
        mockMvc.perform(withGateway(get("/api/v1/disputes/evidence/{fileName}", pngFileName), ADMIN_ID, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(pngBytes));
        mockMvc.perform(withGateway(get("/api/v1/disputes/evidence/{fileName}", pngFileName), OTHER_MERCHANT_ID, "MERCHANT"))
                .andExpect(status().isForbidden());

        // Invalid filename format rejected cleanly -> 400 BAD_REQUEST
        mockMvc.perform(withGateway(get("/api/v1/disputes/evidence/not-a-valid-uuid.png"), DRIVER_ID, "DRIVER"))
                .andExpect(status().isBadRequest());
    }

    private Dispute saveDispute(Long bookingId, Long driverId, DisputeStatusEnum status, LocalDateTime createdAt) {
        Dispute entity = Dispute.builder()
                .bookingId(bookingId)
                .driverId(driverId)
                .merchantId(MERCHANT_ID)
                .parkingLotId(PARKING_LOT_ID)
                .reason("Lý do #" + bookingId)
                .description("Mô tả #" + bookingId)
                .evidenceUrls(List.of("https://cdn.example.com/" + bookingId + ".png"))
                .status(status)
                .resolution(status == DisputeStatusEnum.PENDING ? null : "Kết luận #" + bookingId)
                .build();
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        return disputeRepository.saveAndFlush(entity);
    }

    private MockHttpServletRequestBuilder withGateway(MockHttpServletRequestBuilder builder, Long userId, String role) {
        return builder
                .header(SecurityConstants.HEADER_INTERNAL_SECRET, SecurityConstants.DEFAULT_INTERNAL_SECRET)
                .header(SecurityConstants.HEADER_USER_ID, String.valueOf(userId))
                .header(SecurityConstants.HEADER_USER_ROLE, role);
    }

    private String signJwt(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("phone", "0900000001")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}

