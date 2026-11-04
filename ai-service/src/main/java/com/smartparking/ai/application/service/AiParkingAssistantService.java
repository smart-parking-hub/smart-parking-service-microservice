package com.smartparking.ai.application.service;

import com.smartparking.ai.dto.AiChatDto;
import com.smartparking.ai.dto.ParkingLotDetailResponse;
import com.smartparking.ai.dto.ParkingLotSearchResponse;
import com.smartparking.ai.infrastructure.config.KnowledgeStoreConfig;
import com.smartparking.ai.infrastructure.gemini.GeminiApiClient;
import com.smartparking.ai.infrastructure.tools.ParkingLotTools;
import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

        // 0. Fast-Path: Chào hỏi & Xã giao (hi, hello, xin chào, cảm ơn...) -> Phản hồi NGAY LẬP TỨC (< 5ms)
        if (isGreeting(userMsg)) {
            return handleQuickGreeting();
        }
        if (isPleasantry(userMsg)) {
            return handleQuickPleasantry(userMsg);
        }

        // 1. Nếu có parkingLotId -> Dynamic Lot Policy & Pricing RAG
        if (request.getParkingLotId() != null) {
            Optional<AiChatDto.Response> lotResponse = handleDynamicLotInquiry(request);
            if (lotResponse.isPresent()) {
                return lotResponse.get();
            }
        }

        // 2. Tra cứu Bãi xe theo tên riêng được nhắc tới trong câu hỏi (Named Lot Inquiry)
        // Ví dụ: "bạn biết bãi xe 2imBenn không", "bãi xe Cát Lái ở đâu", "thông tin bãi xe Landmark 81"
        Optional<AiChatDto.Response> namedLotResponse = handleNamedLotInquiry(userMsg);
        if (namedLotResponse.isPresent()) {
            return namedLotResponse.get();
        }

        // 3. Kiểm tra xem có phải câu hỏi về Chính sách / FAQ hay không (Engine 2)
        Optional<KnowledgeStoreConfig.PolicyChunk> policy = knowledgeStore.searchRelevantPolicy(userMsg);
        if (policy.isPresent()) {
            return handlePolicyInquiry(userMsg, policy.get());
        }

        // 4. Kiểm tra xem có phải câu hỏi Tìm kiếm Bãi xe / Slot còn chỗ lân cận hay không (Engine 1: Tool Calling)
        if (isNearbySearchIntent(userMsg)) {
            return handleParkingSearchInquiry(request);
        }

        // 5. Phản hồi chung (General Inquiries với danh sách bãi xe hệ thống)
        return handleGeneralInquiry(userMsg);
    }

    private boolean isGreeting(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase().trim();
        return lower.matches("^(hi|hello|hey|alo|chào|xin chào|chào bạn|chào bot|yo|test)[!.,? ]*$");
    }

    private AiChatDto.Response handleQuickGreeting() {
        return AiChatDto.Response.builder()
                .reply("Xin chào! Tôi là SmartParking AI Copilot. Tôi có thể hỗ trợ bạn tìm kiếm bãi xe còn chỗ trống thời gian thực, tra cứu biểu phí từng bãi hoặc giải đáp quy định giữ chỗ 10 phút và hoàn tiền. Bạn cần hỗ trợ gì hôm nay?")
                .source("GENERAL")
                .suggestedLots(Collections.emptyList())
                .build();
    }

    private boolean isPleasantry(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase().trim();
        return lower.matches("^(cảm ơn|cam on|thanks|thank you|tạm biệt|bye|bye bye|oke|ok|dạ|da|vâng|vang)[!.,? ]*$");
    }

    private AiChatDto.Response handleQuickPleasantry(String msg) {
        String lower = msg.toLowerCase().trim();
        String reply;
        if (lower.contains("cảm ơn") || lower.contains("cam on") || lower.contains("thank")) {
            reply = "Rất vui được hỗ trợ bạn! Chúc bạn có chuyến đi thuận lợi và tìm được chỗ đỗ xe ưng ý.";
        } else if (lower.contains("tạm biệt") || lower.contains("bye")) {
            reply = "Tạm biệt bạn! Khi nào cần tìm chỗ đỗ xe hoặc tra cứu biểu phí, cứ nhắn tôi nhé!";
        } else {
            reply = "Dạ vâng! Nếu bạn cần tìm bãi xe hoặc hỏi chính sách gì cứ nhắn tôi nhé.";
        }
        return AiChatDto.Response.builder()
                .reply(reply)
                .source("GENERAL")
                .suggestedLots(Collections.emptyList())
                .build();
    }

    private Optional<AiChatDto.Response> handleDynamicLotInquiry(AiChatDto.Request request) {
        Long lotId = request.getParkingLotId();
        String userMsg = request.getMessage().trim();
        log.info("Handling Dynamic Lot Inquiry for lotId={}: '{}'", lotId, userMsg);

        Optional<ParkingLotDetailResponse> lotOpt = parkingLotTools.getParkingLotDetail(lotId);
        if (lotOpt.isEmpty()) {
            log.warn("Parking lot not found for lotId={}, falling back to standard flow", lotId);
            return Optional.empty();
        }

        ParkingLotDetailResponse lot = lotOpt.get();
        String lotContext = buildLotContext(lot);

        String systemInstruction = String.format("""
            Bạn là SmartParking AI Copilot - Trợ lý thông minh của hệ sinh thái SmartParkingHub.
            Người dùng đang xem thông tin và hỏi về bãi đỗ xe cụ thể: "%s".
            Hãy trả lời câu hỏi của người dùng một cách chính xác, lịch sự và súc tích dựa trên thông tin bãi xe, số chỗ trống và biểu phí được cung cấp dưới đây.
            Nếu câu hỏi về giá vé hoặc quy định, hãy trích dẫn chuẩn xác số liệu từ bảng giá.
            Tuyệt đối không bịa đặt thông tin ngoài dữ liệu cung cấp.
            """, lot.getName());

        String userPrompt = String.format("""
            THÔNG TIN BÃI XE HIỆN TẠI:
            %s

            CÂU HỎI CỦA NGƯỜI DÙNG:
            %s
            """, lotContext, userMsg);

        Optional<String> aiReply = geminiClient.generateContent(systemInstruction, userPrompt);
        String reply = aiReply.orElseGet(() -> buildFallbackLotReply(lot));

        return Optional.of(AiChatDto.Response.builder()
                .reply(reply)
                .source("DYNAMIC_LOT_RAG")
                .suggestedLots(Collections.emptyList())
                .build());
    }

    private String buildLotContext(ParkingLotDetailResponse lot) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Tên bãi: ").append(lot.getName()).append("\n");
        sb.append("- Địa chỉ: ").append(lot.getAddress()).append("\n");
        if (lot.getRatingAverage() != null) {
            sb.append("- Đánh giá trung bình: ").append(lot.getRatingAverage()).append("/5 sao\n");
        }

        sb.append("- Tình trạng chỗ đỗ:\n");
        if (lot.getSlots() != null && !lot.getSlots().isEmpty()) {
            for (ParkingLotDetailResponse.SlotDto slot : lot.getSlots()) {
                sb.append(String.format("  + Loại xe %s: Còn %d/%d vị trí trống\n",
                        slot.getVehicleType(), slot.getAvailableCapacity(), slot.getTotalCapacity()));
            }
        } else {
            sb.append("  + Đang cập nhật trạng thái chỗ đỗ\n");
        }

        sb.append("- Biểu phí niêm yết:\n");
        if (lot.getPricingRules() != null && !lot.getPricingRules().isEmpty()) {
            for (ParkingLotDetailResponse.PricingRuleDto rule : lot.getPricingRules()) {
                sb.append(String.format("  + %s: Giá cơ bản %s VNĐ/giờ",
                        rule.getVehicleType(),
                        rule.getBasePricePerHour() != null ? String.format("%,.0f", rule.getBasePricePerHour().doubleValue()) : "20,000"));
                if (rule.getSurgeMultiplier() != null && rule.getSurgeMultiplier().doubleValue() > 1.0) {
                    sb.append(String.format(" (Giờ cao điểm %s-%s: hệ số x%.1f)",
                            rule.getSurgeStartTime(), rule.getSurgeEndTime(), rule.getSurgeMultiplier()));
                }
                if (rule.getOvertimePenaltyMultiplier() != null && rule.getOvertimePenaltyMultiplier().doubleValue() > 1.0) {
                    sb.append(String.format(" (Phạt quá giờ: hệ số x%.1f)", rule.getOvertimePenaltyMultiplier()));
                }
                sb.append("\n");
            }
        } else {
            sb.append("  + Giá cơ bản từ 20.000 VNĐ/giờ tùy loại phương tiện\n");
        }

        sb.append("- Quy định chung của bãi:\n");
        sb.append("  + Thời gian giữ chỗ hợp lệ sau khi đặt: Tối đa 10 phút.\n");
        sb.append("  + Check-in và Check-out tự động quét mã QR qua ứng dụng SmartParking.\n");
        sb.append("  + Hủy chỗ trước 15 phút so với giờ hẹn: Hoàn tiền 100% về ví.\n");
        return sb.toString();
    }

    private String buildFallbackLotReply(ParkingLotDetailResponse lot) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Thông tin chính thức về bãi xe **%s** (%s):\n\n", lot.getName(), lot.getAddress()));

        if (lot.getPricingRules() != null && !lot.getPricingRules().isEmpty()) {
            sb.append("💰 **Bảng giá gửi xe:**\n");
            for (ParkingLotDetailResponse.PricingRuleDto rule : lot.getPricingRules()) {
                sb.append(String.format("- **%s**: %,.0f VNĐ/giờ",
                        rule.getVehicleType(),
                        rule.getBasePricePerHour() != null ? rule.getBasePricePerHour().doubleValue() : 20000.0));
                if (rule.getOvertimePenaltyMultiplier() != null && rule.getOvertimePenaltyMultiplier().doubleValue() > 1.0) {
                    sb.append(String.format(" (Quá giờ: x%.1f)", rule.getOvertimePenaltyMultiplier()));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("📋 **Quy định đỗ xe & Đặt chỗ:**\n");
        sb.append("- Đặt chỗ qua app: Giữ chỗ tối đa **10 phút** kể từ lúc bấm đặt.\n");
        sb.append("- Quý khách vui lòng quét mã QR tại barie ra vào để tự động ghi nhận lượt gửi.\n");
        sb.append("- Hủy chỗ trước giờ hẹn 15 phút sẽ được hoàn tiền 100% vào Ví điện tử.");
        return sb.toString();
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

    private Optional<AiChatDto.Response> handleNamedLotInquiry(String userMsg) {
        Optional<String> candidateOpt = extractCandidateLotName(userMsg);
        List<ParkingLotSearchResponse> allLots = parkingLotTools.getAllApprovedParkingLots();

        // 1. Kiểm tra xem có bãi xe nào trong hệ thống khớp với tên ứng viên hoặc query
        Optional<ParkingLotSearchResponse> matchedLotOpt = matchLotByKeyword(allLots, candidateOpt.orElse(null), userMsg);

        if (matchedLotOpt.isPresent()) {
            ParkingLotSearchResponse matchedLot = matchedLotOpt.get();
            log.info("Found matching lot in system for query '{}': id={}, name='{}'", userMsg, matchedLot.getId(), matchedLot.getName());

            // Lấy thông tin chi tiết đầy đủ của bãi xe này
            Optional<ParkingLotDetailResponse> detailOpt = parkingLotTools.getParkingLotDetail(matchedLot.getId());
            String lotContext = detailOpt.map(this::buildLotContext).orElseGet(() ->
                    String.format("- Tên bãi: %s\n- Địa chỉ: %s\n- Giá từ: %,.0f đ/giờ\n- Chỗ trống: %d",
                            matchedLot.getName(), matchedLot.getAddress(),
                            matchedLot.getBasePricePerHour() != null ? matchedLot.getBasePricePerHour().doubleValue() : 20000.0,
                            matchedLot.getAvailableCapacity() != null ? matchedLot.getAvailableCapacity() : 0));

            String systemPrompt = String.format("""
                Bạn là SmartParking AI Copilot. Người dùng đang hỏi về bãi xe "%s".
                Hãy xác nhận bạn biết bãi xe này, tóm tắt thông tin quan trọng (địa chỉ, số chỗ trống, biểu phí niêm yết) một cách lịch sự, súc tích (3-4 câu) và hướng dẫn người dùng bấm nút đặt chỗ bên dưới.
                """, matchedLot.getName());
            String userPrompt = String.format("THÔNG TIN BÃI XE:\n%s\n\nCÂU HỎI CỦA NGƯỜI DÙNG: %s", lotContext, userMsg);

            Optional<String> aiReply = geminiClient.generateContent(systemPrompt, userPrompt);
            String reply = aiReply.orElseGet(() -> String.format(
                    "Tôi biết bãi xe **%s**! Bãi xe tọa lạc tại: %s.\n- Vị trí trống hiện tại: **%d chỗ**\n- Giá cơ bản: **%,.0f VNĐ/giờ**\nBạn có thể bấm vào thẻ bên dưới để xem chi tiết và đặt chỗ ngay nhé!",
                    matchedLot.getName(), matchedLot.getAddress(),
                    matchedLot.getAvailableCapacity() != null ? matchedLot.getAvailableCapacity() : 0,
                    matchedLot.getBasePricePerHour() != null ? matchedLot.getBasePricePerHour().doubleValue() : 20000.0
            ));

            AiChatDto.SuggestedParkingLot suggested = AiChatDto.SuggestedParkingLot.builder()
                    .lotId(matchedLot.getId())
                    .lotName(matchedLot.getName())
                    .address(matchedLot.getAddress())
                    .distanceKm(matchedLot.getDistanceKm())
                    .availableSlots(matchedLot.getAvailableCapacity())
                    .pricePerHour(matchedLot.getBasePricePerHour() != null ? matchedLot.getBasePricePerHour().doubleValue() : 20000.0)
                    .averageRating(matchedLot.getAverageRating())
                    .bookingUrl(String.format("/dashboard/parking/%d", matchedLot.getId()))
                    .build();

            return Optional.of(AiChatDto.Response.builder()
                    .reply(reply)
                    .source("DYNAMIC_LOT_RAG")
                    .suggestedLots(Collections.singletonList(suggested))
                    .build());
        }

        // 2. Nếu người dùng đích danh hỏi về một bãi xe cụ thể (có candidate) nhưng KHÔNG tìm thấy trong hệ thống
        if (candidateOpt.isPresent()) {
            String candidateName = candidateOpt.get();
            log.info("Candidate lot '{}' not found in system for query '{}'", candidateName, userMsg);

            String systemPrompt = String.format("""
                Bạn là SmartParking AI Copilot của hệ sinh thái SmartParkingHub.
                Người dùng đang hỏi về bãi xe có tên "%s".
                Tuy nhiên, trong cơ sở dữ liệu của hệ thống hiện KHÔNG CÓ bãi xe nào mang tên này (chưa đăng ký hoặc chưa liên kết).
                Hãy giải thích rõ ràng và lịch sự rằng hệ thống chưa có thông tin về bãi xe này, đồng thời gợi ý người dùng cung cấp khu vực (quận/đường) để bạn tìm kiếm bãi xe lân cận.
                """, candidateName);

            Optional<String> aiReply = geminiClient.generateContent(systemPrompt, userMsg);
            String reply = aiReply.orElseGet(() -> String.format(
                    "Rất tiếc, hiện tại hệ thống SmartParkingHub chưa có dữ liệu hoặc chưa liên kết với bãi xe **\"%s\"** (hoặc bãi này chưa được đăng ký/phê duyệt trên ứng dụng).\n\nBạn có thể kiểm tra lại tên bãi hoặc cho tôi biết địa chỉ/khu vực bạn cần gửi xe để tôi hỗ trợ tìm kiếm các bãi xe đang hoạt động gần nhất nhé!",
                    candidateName
            ));

            return Optional.of(AiChatDto.Response.builder()
                    .reply(reply)
                    .source("GENERAL")
                    .suggestedLots(Collections.emptyList())
                    .build());
        }

        return Optional.empty();
    }

    private Optional<String> extractCandidateLotName(String msg) {
        if (msg == null || msg.isBlank()) return Optional.empty();
        String trimmed = msg.trim();

        // Pattern 1: Hỏi xem có biết bãi xe X không: "bạn biết bãi xe 2imBenn không", "có biết bãi 2imBenn ko"
        Pattern p1 = Pattern.compile(
                "(?i)(?:bạn\\s+)?(?:có\\s+)?biết\\s+bãi\\s+(?:đỗ\\s+)?(?:xe\\s+)?([\\p{L}0-9_.-]+(?:\\s+[\\p{L}0-9_.-]+){0,3})\\s*(?:không|ko|k|chưa|nhỉ|\\?|$)",
                Pattern.UNICODE_CHARACTER_CLASS
        );
        Matcher m1 = p1.matcher(trimmed);
        if (m1.find()) {
            String candidate = m1.group(1).trim();
            if (!isGenericWord(candidate.toLowerCase()) && candidate.length() >= 2) {
                return Optional.of(candidate);
            }
        }

        // Pattern 2: Hỏi thông tin/vị trí bãi xe cụ thể: "bãi xe Landmark 81 ở đâu", "thông tin bãi xe Cát Lái"
        Pattern p2 = Pattern.compile(
                "(?i)(?:thông tin|địa chỉ|vị trí|hỏi về)?\\s*bãi\\s+(?:đỗ\\s+)?(?:xe\\s+)?([\\p{L}0-9_.-]+(?:\\s+[\\p{L}0-9_.-]+){0,3})\\s+(?:ở đâu|nằm ở đâu|như thế nào|thế nào|ra sao|có gì|có mở cửa không|\\?)",
                Pattern.UNICODE_CHARACTER_CLASS
        );
        Matcher m2 = p2.matcher(trimmed);
        if (m2.find()) {
            String candidate = m2.group(1).trim();
            if (!isGenericWord(candidate.toLowerCase()) && candidate.length() >= 2) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    private boolean isGenericWord(String word) {
        if (word == null) return true;
        String w = word.trim().toLowerCase();
        return w.matches("^(nào|gần đây|gần tôi|gần nhất|này|kia|đó|ấy|trống|nhiều chỗ|giá rẻ|ô tô|xe máy|xe tải|tải|container|khách|bus)$");
    }

    private Optional<ParkingLotSearchResponse> matchLotByKeyword(List<ParkingLotSearchResponse> allLots, String keyword, String fullQuery) {
        if (allLots == null || allLots.isEmpty()) return Optional.empty();

        String lowerKeyword = keyword != null ? keyword.toLowerCase().trim() : "";
        String lowerQuery = fullQuery != null ? fullQuery.toLowerCase().trim() : "";

        // 1. Khớp chính xác theo candidate keyword nếu có
        if (!lowerKeyword.isBlank()) {
            for (ParkingLotSearchResponse lot : allLots) {
                if (lot.getName() != null && lot.getName().toLowerCase().contains(lowerKeyword)) {
                    return Optional.of(lot);
                }
            }
        }

        // 2. Khớp nếu fullQuery chứa tên bãi xe hoặc các định danh đặc trưng
        for (ParkingLotSearchResponse lot : allLots) {
            if (lot.getName() != null) {
                String lotNameLower = lot.getName().toLowerCase();
                String simplifiedName = lotNameLower
                        .replace("bãi đỗ xe", "")
                        .replace("bãi xe", "")
                        .replace("bãi", "")
                        .trim();
                if (!simplifiedName.isBlank() && simplifiedName.length() >= 3 && lowerQuery.contains(simplifiedName)) {
                    return Optional.of(lot);
                }
            }
        }

        return Optional.empty();
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
            String fallbackReply = String.format("Tôi đã tìm thấy %d bãi đỗ xe còn vị trí trống phù hợp cho loại xe của bạn. Bạn có thể xem chi tiết và bấm đặt chỗ ngay bên dưới:", suggested.size());
            String lotsSummary = suggested.stream()
                    .map(s -> String.format("- %s: còn %d chỗ trống, giá %,.0f đ/giờ (cách %.1f km)",
                            s.getLotName(), s.getAvailableSlots(), s.getPricePerHour(), s.getDistanceKm()))
                    .collect(Collectors.joining("\n"));

            String systemPrompt = """
                Bạn là SmartParking AI Copilot. Người dùng đang tìm chỗ đỗ xe lân cận.
                Hãy viết một phản hồi ngắn gọn (2-3 câu), thân thiện thông báo về các bãi đỗ xe còn chỗ trống được tìm thấy và hướng dẫn người dùng bấm nút đặt chỗ bên dưới.
                """;
            String userPrompt = String.format("Câu hỏi: %s\nBãi xe tìm thấy:\n%s", request.getMessage(), lotsSummary);
            reply = geminiClient.generateContent(systemPrompt, userPrompt).orElse(fallbackReply);
        }

        return AiChatDto.Response.builder()
                .reply(reply)
                .source("REALTIME_SLOTS")
                .suggestedLots(suggested)
                .build();
    }

    private AiChatDto.Response handleGeneralInquiry(String userMsg) {
        List<ParkingLotSearchResponse> allLots = parkingLotTools.getAllApprovedParkingLots();
        StringBuilder lotsContext = new StringBuilder();
        if (!allLots.isEmpty()) {
            lotsContext.append("CÁC BÃI XE CHÍNH THỨC ĐANG HOẠT ĐỘNG TRONG HỆ THỐNG SMARTPARKINGHUB:\n");
            for (ParkingLotSearchResponse lot : allLots.subList(0, Math.min(allLots.size(), 10))) {
                lotsContext.append(String.format("- %s (Địa chỉ: %s)\n", lot.getName(), lot.getAddress()));
            }
        }

        String systemInstruction = String.format("""
            Bạn là SmartParking AI Copilot - Trợ lý số thông minh của hệ thống SmartParkingHub.
            Hãy chào hỏi thân thiện, hướng dẫn tài xế cách tìm bãi xe, đặt giữ chỗ tạm thời trong 10 phút, hoặc hỏi về các chính sách bồi thường và hoàn tiền.
            Nếu người dùng hỏi về một bãi xe cụ thể mà không nằm trong danh sách bãi xe của hệ sinh thái SmartParkingHub, hãy nói rõ hệ thống chưa liên kết với bãi xe đó và hỏi người dùng khu vực cần đỗ để hỗ trợ tìm bãi gần nhất.
            Tuyệt đối không bịa đặt thông tin bãi xe ngoài dữ liệu cung cấp.

            %s
            """, lotsContext.toString());

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

    private boolean isNearbySearchIntent(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("tìm bãi") || lower.contains("tìm chỗ") || lower.contains("kiếm bãi")
                || lower.contains("kiếm chỗ") || lower.contains("gần đây") || lower.contains("gần tôi")
                || lower.contains("gần nhất") || lower.contains("quanh đây") || lower.contains("xung quanh")
                || lower.contains("còn chỗ trống") || lower.contains("còn slot trống")
                || lower.contains("bãi nào còn chỗ") || lower.contains("tìm nơi đỗ");
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
