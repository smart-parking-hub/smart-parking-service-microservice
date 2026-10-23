package com.smartparking.identity.application.service;

import com.smartparking.identity.application.dto.MerchantProfileResponse;
import com.smartparking.identity.application.dto.UpdateMerchantProfileRequest;
import org.springframework.web.multipart.MultipartFile;

public interface MerchantProfileService {
    MerchantProfileResponse getProfile(Long merchantUserId);
    MerchantProfileResponse updateProfile(Long merchantUserId, UpdateMerchantProfileRequest request);
    MerchantProfileResponse uploadDocument(Long merchantUserId, String businessLicense, MultipartFile file);
}
