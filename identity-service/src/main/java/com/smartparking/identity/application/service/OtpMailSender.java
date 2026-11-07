package com.smartparking.identity.application.service;

import com.smartparking.common.enums.OtpPurposeEnum;

public interface OtpMailSender {
    void send(String email, String code, OtpPurposeEnum purpose);
}
