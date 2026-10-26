package com.smartparking.ai.application.service;

import com.smartparking.ai.dto.AiChatDto;
import com.smartparking.ai.infrastructure.config.KnowledgeStoreConfig;
import com.smartparking.ai.infrastructure.gemini.GeminiApiClient;
import com.smartparking.ai.infrastructure.tools.ParkingLotTools;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.ai.dto.ParkingLotSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiParkingAssistantService {

    private final GeminiApiClient geminiClient;
    private final KnowledgeStoreConfig knowledgeStore;
    private final ParkingLotTools parkingLotTools;

    public AiChatDto.Response processDriverChat(AiChatDto.Request request, String userId) {
        String userMsg = request.getMessage().trim();
        log.info("Processing Driver Chat query from user {}: '{}'", userId, userMsg);

        // 1. Kiểm tra xem có phải câu hỏi về Chính sách / FAQ hay không (Engine 2)
        Optional<KnowledgeStoreConfig.PolicyChunk> policy = knowledgeStore.searchRelevantPolicy(userMsg);
        if (policy.isPresent()) {
            return handlePolicyInquiry(userMsg, policy.get());
        }

        // 2. Kiểm tra xem có phải câu hỏi Tìm kiếm Bãi xe / Slot còn chỗ hay không (Engine 1: Tool Calling)
        if (isSearchIntent(userMsg)) {
            return handleParkingSearchInquiry(request);
        }

        // 3. Phản hồi chung (General Inquiries)
        return handleGeneralInquiry(userMsg);
    }

    private AiChatDto.Response handlePolicyInquiry(String userMsg, KnowledgeStoreConfig.PolicyChunk policy) {
        String systemInstruction = """
            Bạn là SmartParking AI Copilot - Trợ lý thông minh của hệ sinh thái SmartParkingHub.
            Hãy trả lời câu hỏi của tài xế một cách súc tích, lịch sự và dựa CHÍNH XÁC trên điều khoản quy định được cung cấp.
            Tuyệt đối không bịa đặt thêm quyền lợi hay chính sách ngoài văn bản.
            """;

        String userPrompt = String.format("Quy định chính thức:\n%s: %s\n\nCâu hỏi tài xế: %s",
                policy.title(), policy.content(), userMsg);

        Optional<String> aiReply = geminiClient.generateContent(systemInstruction, userPrompt);
        String reply = aiReply.orElseGet(() -> String.format(
                "Theo quy định chính thức của SmartParkingHub về '%s':\n\n%s",
                policy.title(), policy.content()
        ));

        return AiChatDto.Response.builder()
                .reply(reply)
                .source("POLICY_FAQ")
                .suggestedLots(Collections.emptyList())
                .build();
    }

    private AiChatDto.Response handleParkingSearchInquiry(AiChatDto.Request request) {
        VehicleTypeEnum vehicleType = detectVehicleType(request.getMessage());
        Double lat = request.getUserLat() != null ? request.getUserLat() : 10.776889;
        Double lon = request.getUserLon() != null ? request.getUserLon() : 106.700806;

        // Kích hoạt Tool Calling: Gọi sang parking-lot-service
        List<ParkingLotSearchResponse> lots = parkingLotTools.findAvailableParkingLots(vehicleType, lat, lon, 15.0);

        List<AiChatDto.SuggestedParkingLot> suggested = new ArrayList<>();
        for (ParkingLotSearchResponse lot : lots) {
            suggested.add(AiChatDto.SuggestedParkingLot.builder()
                    .lotId(lot.getId())
                    .lotName(lot.getName())
                    .address(lot.getAddress())
                    .distanceKm(lot.getDistanceKm())
                    .availableSlots(lot.getAvailableCapacity())
                    .pricePerHour(lot.getBasePricePerHour() != null ? lot.getBasePricePerHour().doubleValue() : 20000.0)
                    .averageRating(lot.getAverageRating())
                    .bookingUrl(String.format("/dashboard/parking/%d", lot.getId()))
                    .build());
        }

        String reply;
        if (suggested.isEmpty()) {
            reply = "Rất tiếc, hiện tại không tìm thấy bãi đỗ xe nào còn chỗ trống phù hợp với loại phương tiện của bạn trong bán kính 15km. Bạn có thể mở rộng phạm vi tìm kiếm hoặc thử lại sau ít phút.";
        } else {
            reply = String.format("Tôi đã tìm thấy %d bãi đỗ xe còn vị trí trống phù hợp cho loại xe của bạn. Bạn có thể xem chi tiết và bấm đặt chỗ ngay bên dưới:", suggested.size());
        }

        return AiChatDto.Response.builder()
                .reply(reply)
                .source("REALTIME_SLOTS")
                .suggestedLots(suggested)
                .build();
    }

    private AiChatDto.Response handleGeneralInquiry(String userMsg) {
        String systemInstruction = """
            Bạn là SmartParking AI Copilot - Trợ lý số thông minh của hệ thống SmartParkingHub.
            Hãy chào hỏi thân thiện, hướng dẫn tài xế cách tìm bãi xe, đặt giữ chỗ tạm thời trong 10 phút, hoặc hỏi về các chính sách bồi thường và hoàn tiền.
            """;

        Optional<String> aiReply = geminiClient.generateContent(systemInstruction, userMsg);
        String reply = aiReply.orElse(
                "Xin chào! Tôi là SmartParking AI Copilot. Tôi có thể hỗ trợ bạn tìm kiếm bãi xe còn chỗ trống thời gian thực, hướng dẫn đặt giữ chỗ trong 10 phút hoặc giải đáp các thắc mắc về chính sách hủy vé và thanh toán. Bạn cần hỗ trợ gì hôm nay?"
        );

        return AiChatDto.Response.builder()
                .reply(reply)
                .source("GENERAL")
                .suggestedLots(Collections.emptyList())
                .build();
    }

    private boolean isSearchIntent(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("tìm") || lower.contains("bãi") || lower.contains("chỗ")
                || lower.contains("đỗ") || lower.contains("slot") || lower.contains("gần")
                || lower.contains("parking") || lower.contains("trống");
    }

    private VehicleTypeEnum detectVehicleType(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("tải nhỏ") || lower.contains("xe tải")) return VehicleTypeEnum.LIGHT_TRUCK;
        if (lower.contains("container") || lower.contains("đầu kéo")) return VehicleTypeEnum.CONTAINER;
        if (lower.contains("tải nặng") || lower.contains("xe ben")) return VehicleTypeEnum.HEAVY_TRUCK;
        if (lower.contains("xe buýt") || lower.contains("xe khách") || lower.contains("bus")) return VehicleTypeEnum.BUS_COACH;
        return VehicleTypeEnum.SEDAN_SUV;
    }
}
