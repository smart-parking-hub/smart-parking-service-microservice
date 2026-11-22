package com.smartparking.identity.application.service.verification;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public final class VerificationUploadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    private VerificationUploadException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static VerificationUploadException tooLarge(String message) {
        return new VerificationUploadException(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    public static VerificationUploadException unsupportedType(String message) {
        return new VerificationUploadException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}
