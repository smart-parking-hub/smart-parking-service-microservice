package com.smartparking.ai.application.service;

import com.smartparking.ai.dto.AiChatDto;
import com.smartparking.ai.dto.ParkingLotDetailResponse;
import com.smartparking.ai.dto.ParkingLotSearchResponse;
import com.smartparking.ai.infrastructure.config.KnowledgeStoreConfig;
import com.smartparking.ai.infrastructure.gemini.GeminiApiClient;
import com.smartparking.ai.infrastructure.tools.ParkingLotTools;
import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiParkingAssistantServiceTest {

    @Mock
    private GeminiApiClient geminiClient;

    @Mock
    private KnowledgeStoreConfig knowledgeStore;

    @Mock
    private ParkingLotTools parkingLotTools;

    @InjectMocks
    private AiParkingAssistantService service;

    @Test
    @DisplayName("AAA - processDriverChat: Should recognize policy inquiry and return POLICY_FAQ source")
    void processDriverChat_policyQuery_shouldReturnPolicyResponse() {
        // Arrange
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("Quy định hủy chỗ và hoàn tiền thế nào?")
                .build();

        KnowledgeStoreConfig.PolicyChunk mockPolicy = new KnowledgeStoreConfig.PolicyChunk(
                "Chính Sách Hủy Chỗ",
                "Tài xế có thể hủy đặt chỗ trước giờ hẹn 15 phút mà không mất phí."
        );
        when(knowledgeStore.searchRelevantPolicy("Quy định hủy chỗ và hoàn tiền thế nào?"))
                .thenReturn(Optional.of(mockPolicy));
        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        AiChatDto.Response response = service.processDriverChat(request, "1024");

        // Assert
        assertNotNull(response);
        assertEquals("POLICY_FAQ", response.getSource());
        assertTrue(response.getReply().contains("15 phút"));
    }

    @Test
    @DisplayName("AAA - processDriverChat: Should execute Dynamic Lot RAG when parkingLotId is provided")
    void processDriverChat_withParkingLotId_shouldFetchLotDetailAndReturnDynamicLotRag() {
        // Arrange
        Long lotId = 10L;
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("Phí gửi xe ở bãi này tính như thế nào?")
                .parkingLotId(lotId)
                .build();

        ParkingLotDetailResponse.PricingRuleDto pricingRule = ParkingLotDetailResponse.PricingRuleDto.builder()
                .id(1L)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .basePricePerHour(BigDecimal.valueOf(25000))
                .surgeMultiplier(BigDecimal.valueOf(1.2))
                .surgeStartTime(LocalTime.of(7, 0))
                .surgeEndTime(LocalTime.of(9, 0))
                .overtimePenaltyMultiplier(BigDecimal.valueOf(1.5))
                .build();

        ParkingLotDetailResponse.SlotDto slot = ParkingLotDetailResponse.SlotDto.builder()
                .id(1L)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .totalCapacity(50)
                .availableCapacity(18)
                .build();

        ParkingLotDetailResponse mockLot = ParkingLotDetailResponse.builder()
                .id(lotId)
                .name("Bãi Đỗ Xe Landmark 81")
                .address("720A Điện Biên Phủ, P. 22, Bình Thạnh")
                .status(ParkingLotStatusEnum.ACTIVE)
                .ratingAverage(BigDecimal.valueOf(4.8))
                .slots(List.of(slot))
                .pricingRules(List.of(pricingRule))
                .build();

        when(parkingLotTools.getParkingLotDetail(lotId)).thenReturn(Optional.of(mockLot));
        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        AiChatDto.Response response = service.processDriverChat(request, "1024");

        // Assert
        assertNotNull(response);
        assertEquals("DYNAMIC_LOT_RAG", response.getSource());
        assertTrue(response.getReply().contains("Bãi Đỗ Xe Landmark 81"));
        assertTrue(response.getReply().contains("25,000 VNĐ/giờ") || response.getReply().contains("25.000 VNĐ/giờ") || response.getReply().contains("25000"));
        assertTrue(response.getReply().contains("10 phút"));
    }

    @Test
    @DisplayName("AAA - processDriverChat: Should fallback to FAQ when parkingLotId not found")
    void processDriverChat_withParkingLotId_whenLotNotFound_shouldFallbackGracefully() {
        // Arrange
        Long missingLotId = 9999L;
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("Quy định hủy chỗ thế nào?")
                .parkingLotId(missingLotId)
                .build();

        when(parkingLotTools.getParkingLotDetail(missingLotId)).thenReturn(Optional.empty());

        KnowledgeStoreConfig.PolicyChunk mockPolicy = new KnowledgeStoreConfig.PolicyChunk(
                "Chính Sách Hủy Chỗ",
                "Tài xế có thể hủy đặt chỗ trước giờ hẹn 15 phút mà không mất phí."
        );
        when(knowledgeStore.searchRelevantPolicy("Quy định hủy chỗ thế nào?"))
                .thenReturn(Optional.of(mockPolicy));
        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        AiChatDto.Response response = service.processDriverChat(request, "1024");

        // Assert
        assertNotNull(response);
        assertEquals("POLICY_FAQ", response.getSource());
        assertTrue(response.getReply().contains("15 phút"));
    }

    @Test
    @DisplayName("AAA - processDriverChat: Should execute Tool Calling and return REALTIME_SLOTS with suggested lots")
    void processDriverChat_searchQuery_shouldCallToolAndReturnLots() {
        // Arrange
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("Tìm bãi xe ô tô gần Landmark 81 còn slot trống")
                .userLat(10.795)
                .userLon(106.721)
                .build();

        when(knowledgeStore.searchRelevantPolicy(anyString())).thenReturn(Optional.empty());

        ParkingLotSearchResponse mockLot = ParkingLotSearchResponse.builder()
                .id(1L)
                .name("Bãi Xe Landmark Plus")
                .address("208 Nguyễn Hữu Cảnh, Bình Thạnh")
                .availableCapacity(12)
                .basePricePerHour(BigDecimal.valueOf(25000))
                .averageRating(4.9)
                .distanceKm(0.8)
                .build();

        when(parkingLotTools.findAvailableParkingLots(eq(VehicleTypeEnum.SEDAN_SUV), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(mockLot));

        // Act
        AiChatDto.Response response = service.processDriverChat(request, "1024");

        // Assert
        assertNotNull(response);
        assertEquals("REALTIME_SLOTS", response.getSource());
        assertFalse(response.getSuggestedLots().isEmpty());
        assertEquals(1, response.getSuggestedLots().size());
        assertEquals("Bãi Xe Landmark Plus", response.getSuggestedLots().get(0).getLotName());
        assertEquals(12, response.getSuggestedLots().get(0).getAvailableSlots());
    }

    @Test
    @DisplayName("AAA - processDriverChat: Should handle named lot inquiry when lot exists in system")
    void processDriverChat_namedLotExists_shouldReturnMatchedLotInfo() {
        // Arrange
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("bạn biết bãi xe Cát Lái không")
                .build();

        ParkingLotSearchResponse mockLot = ParkingLotSearchResponse.builder()
                .id(1L)
                .name("Bãi Đỗ Xe Container Cát Lái - Cổng G")
                .address("Số 789 Nguyễn Thị Định, Cát Lái, Thủ Đức")
                .availableCapacity(10)
                .basePricePerHour(BigDecimal.valueOf(35000))
                .averageRating(4.9)
                .distanceKm(2.5)
                .build();

        when(parkingLotTools.getAllApprovedParkingLots()).thenReturn(List.of(mockLot));
        when(parkingLotTools.getParkingLotDetail(1L)).thenReturn(Optional.empty());
        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        AiChatDto.Response response = service.processDriverChat(request, "1024");

        // Assert
        assertNotNull(response);
        assertEquals("DYNAMIC_LOT_RAG", response.getSource());
        assertFalse(response.getSuggestedLots().isEmpty());
        assertEquals("Bãi Đỗ Xe Container Cát Lái - Cổng G", response.getSuggestedLots().get(0).getLotName());
        assertTrue(response.getReply().contains("Cát Lái"));
    }

    @Test
    @DisplayName("AAA - processDriverChat: Should handle named lot inquiry when lot does NOT exist in system")
    void processDriverChat_namedLotDoesNotExist_shouldReturnClearNotFoundExplanation() {
        // Arrange
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("bạn biết bãi xe 2imBenn không")
                .build();

        when(parkingLotTools.getAllApprovedParkingLots()).thenReturn(List.of());
        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        AiChatDto.Response response = service.processDriverChat(request, "1024");

        // Assert
        assertNotNull(response);
        assertEquals("GENERAL", response.getSource());
        assertTrue(response.getSuggestedLots().isEmpty());
        assertTrue(response.getReply().contains("2imBenn"));
        assertTrue(response.getReply().contains("chưa có dữ liệu") || response.getReply().contains("chưa liên kết"));
    }

    @Test
    @DisplayName("AAA - processDriverChat: Should handle general inquiry politely")
    void processDriverChat_generalInquiry_shouldReturnGreeting() {
        // Arrange
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("Xin chào bạn là ai")
                .build();

        when(knowledgeStore.searchRelevantPolicy(anyString())).thenReturn(Optional.empty());
        when(geminiClient.generateContent(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        AiChatDto.Response response = service.processDriverChat(request, "guest");

        // Assert
        assertNotNull(response);
        assertEquals("GENERAL", response.getSource());
        assertTrue(response.getReply().contains("SmartParking AI Copilot"));
    }
}
