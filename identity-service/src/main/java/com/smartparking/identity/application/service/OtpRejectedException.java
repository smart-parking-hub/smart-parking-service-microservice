package com.smartparking.identity.application.service;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;

/** Commits the failed-attempt counter; thrown before any account mutation. */
public class OtpRejectedException extends AppException {
    private static final long serialVersionUID = 1L;

    public OtpRejectedException() {
        super(ErrorCode.BAD_REQUEST, "Mã OTP không chính xác!");
    }
}
