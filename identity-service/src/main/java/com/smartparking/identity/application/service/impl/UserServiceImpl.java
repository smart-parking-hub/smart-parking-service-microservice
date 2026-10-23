package com.smartparking.identity.application.service.impl;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.ChangePasswordRequest;
import com.smartparking.identity.application.dto.UserProfileDto;
import com.smartparking.identity.application.service.UserService;
import com.smartparking.identity.domain.model.Driver;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.DriverRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        return UserProfileDto.builder()
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .emergencyContact(user.getEmergencyContact())
                .address(user.getAddress())
                .build();
    }

    @Override
    @Transactional
    public UserProfileDto updateProfile(Long userId, UserProfileDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setEmergencyContact(request.getEmergencyContact());
        user.setAddress(request.getAddress());
        userRepository.save(user);

        Driver driver = driverRepository.findByUserId(userId).orElse(null);
        if (driver != null && request.getFullName() != null) {
            driver.setFullName(request.getFullName());
            driverRepository.save(driver);
        }

        return request;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
