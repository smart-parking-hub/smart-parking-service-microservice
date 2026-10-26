package com.smartparking.ai.application.service;

import com.smartparking.ai.dto.AiChatDto;
import com.smartparking.ai.dto.ParkingLotSearchResponse;
import com.smartparking.ai.infrastructure.config.KnowledgeStoreConfig;
import com.smartparking.ai.infrastructure.gemini.GeminiApiClient;
import com.smartparking.ai.infrastructure.tools.ParkingLotTools;
import com.smartparking.common.enums.VehicleTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
