package com.smartparking.identity.application.service;

import com.smartparking.identity.application.dto.ChangePasswordRequest;
import com.smartparking.identity.application.dto.UserProfileDto;

public interface UserService {
    UserProfileDto getProfile(Long userId);
    UserProfileDto updateProfile(Long userId, UserProfileDto request);
    void changePassword(Long userId, ChangePasswordRequest request);
}
