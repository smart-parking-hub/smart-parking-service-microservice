package com.smartparking.payment.application.dto;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;

public enum DriverDisputeStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    REJECTED;

    public static DriverDisputeStatus fromEntity(DisputeStatusEnum status, boolean hasMerchantResponse) {
        if (status == null || status == DisputeStatusEnum.PENDING) {
            return hasMerchantResponse ? IN_REVIEW : OPEN;
        }
        return switch (status) {
            case PENDING -> hasMerchantResponse ? IN_REVIEW : OPEN;
            case RESOLVED -> RESOLVED;
            case REJECTED -> REJECTED;
        };
    }

    public static DriverDisputeStatus fromStrictParam(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái khiếu nại không hợp lệ");
        }
        return switch (raw) {
            case "OPEN" -> OPEN;
            case "IN_REVIEW" -> IN_REVIEW;
            case "RESOLVED" -> RESOLVED;
            case "REJECTED" -> REJECTED;
            default -> throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái khiếu nại không hợp lệ");
        };
    }
}

