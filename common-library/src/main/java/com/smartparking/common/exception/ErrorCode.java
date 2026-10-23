package com.smartparking.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INTERNAL_SERVER_ERROR("ERR_500", "Lỗi máy chủ nội bộ"),
    BAD_REQUEST("ERR_400", "Dữ liệu yêu cầu không hợp lệ"),
    UNAUTHORIZED("ERR_401", "Chưa xác thực hoặc token không hợp lệ"),
    FORBIDDEN("ERR_403", "Không có quyền truy cập tài nguyên"),
    NOT_FOUND("ERR_404", "Không tìm thấy tài nguyên yêu cầu"),
    CONFLICT("ERR_409", "Dữ liệu bị xung đột hoặc đã tồn tại"),
    
    // Auth errors
    USER_NOT_FOUND("AUTH_001", "Người dùng không tồn tại"),
    INVALID_CREDENTIALS("AUTH_002", "Sai tài khoản hoặc mật khẩu"),
    TOKEN_EXPIRED("AUTH_003", "Phiên đăng nhập đã hết hạn"),

    // Parking & Slot errors
    PARKING_LOT_NOT_FOUND("PARK_001", "Không tìm thấy bãi xe"),
    SLOT_NOT_AVAILABLE("PARK_002", "Chỗ đỗ xe đã có người giữ hoặc đang bảo trì"),

    // Booking errors
    BOOKING_NOT_FOUND("BOOK_001", "Không tìm thấy lượt đặt chỗ"),
    BOOKING_STATUS_INVALID("BOOK_002", "Trạng thái đặt chỗ không cho phép thao tác này"),
    BOOKING_HOLD_EXPIRED("BOOK_003", "Thời gian giữ chỗ tạm thời đã hết hạn"),

    // Payment errors
    PAYMENT_FAILED("PAY_001", "Giao dịch thanh toán thất bại"),
    WALLET_INSUFFICIENT_BALANCE("PAY_002", "Số dư ví không đủ để thanh toán"),
    IDEMPOTENCY_KEY_DUPLICATED("PAY_003", "Yêu cầu đã được xử lý trước đó");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
