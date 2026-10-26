package com.smartparking.ai.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.ai.application.service.AiContentGenerationService;
import com.smartparking.ai.application.service.AiParkingAssistantService;
import com.smartparking.ai.dto.AiChatDto;
import com.smartparking.ai.dto.MockDataDto;
import com.smartparking.ai.dto.ParkingDescriptionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiContentGenerationService contentGenerationService;

    @MockBean
    private AiParkingAssistantService assistantService;

    @Test
    @DisplayName("AAA - POST /api/v1/ai/chat: Should return 200 OK with AI response")
    void chatWithAssistant_validRequest_returns200() throws Exception {
        // Arrange
        AiChatDto.Request request = AiChatDto.Request.builder()
                .message("Tìm bãi xe gần chợ Bến Thành")
                .build();

        AiChatDto.Response mockResponse = AiChatDto.Response.builder()
                .reply("Đã tìm thấy bãi xe phù hợp.")
                .source("REALTIME_SLOTS")
                .suggestedLots(List.of())
                .build();

        when(assistantService.processDriverChat(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").value("Đã tìm thấy bãi xe phù hợp."))
                .andExpect(jsonPath("$.data.source").value("REALTIME_SLOTS"));
    }

    @Test
    @DisplayName("AAA - POST /api/v1/ai/generate-description: Should return 200 OK")
    void generateParkingDescription_validRequest_returns200() throws Exception {
        // Arrange
        ParkingDescriptionDto.Request request = ParkingDescriptionDto.Request.builder()
                .lotName("Bãi Xe Bình Thạnh")
                .address("Điện Biên Phủ, Bình Thạnh")
                .totalCapacity(50)
                .build();

        ParkingDescriptionDto.Response mockResponse = ParkingDescriptionDto.Response.builder()
                .marketingDescription("Mô tả hấp dẫn")
                .operatingRegulations("Nội quy chuẩn")
                .wayfindingGuide("Chỉ đường")
                .build();

        when(contentGenerationService.generateParkingContent(any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.marketingDescription").value("Mô tả hấp dẫn"));
    }

    @Test
    @DisplayName("AAA - POST /api/v1/ai/generate-mock-data: Should return 200 OK")
    void generateMockData_validRequest_returns200() throws Exception {
        // Arrange
        MockDataDto.Request request = MockDataDto.Request.builder()
                .targetArea("Quận 7, TP.HCM")
                .count(2)
                .build();

        MockDataDto.MockParkingLot mockLot = MockDataDto.MockParkingLot.builder()
                .name("Bãi Xe Phú Mỹ Hưng")
                .address("Nguyễn Văn Linh, Quận 7")
                .totalCapacity(80)
                .basePricePerHour(20000.0)
                .build();

        when(contentGenerationService.generateMockParkingLots(any())).thenReturn(List.of(mockLot));

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate-mock-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Bãi Xe Phú Mỹ Hưng"));
    }
}
