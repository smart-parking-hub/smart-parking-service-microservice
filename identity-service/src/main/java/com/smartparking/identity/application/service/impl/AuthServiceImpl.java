package com.smartparking.identity.application.service.impl;

import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.*;
import com.smartparking.identity.application.service.AuthService;
import com.smartparking.identity.domain.model.Driver;
import com.smartparking.identity.domain.model.Merchant;
import com.smartparking.identity.domain.model.OtpChallenge;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.DriverRepository;
import com.smartparking.identity.domain.repository.MerchantRepository;
import com.smartparking.identity.domain.repository.OtpChallengeRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import com.smartparking.identity.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.smartparking.identity.application.service.OtpMailSender;
import com.smartparking.identity.application.service.OtpRejectedException;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_TTL_SECONDS = 300;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final MerchantRepository merchantRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    private final OtpMailSender otpMailSender;

    @Override
    public AuthResponse login(AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getPhone(), request.getPassword())
            );

            String jwt = jwtTokenProvider.generateJwtToken(authentication);
            User user = userRepository.findByPhone(request.getPhone())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Người dùng không tồn tại"));

            log.info("User {} logged in successfully", request.getPhone());
            return AuthResponse.builder()
                    .token(jwt)
                    .role(user.getRole().name())
                    .build();
        } catch (BadCredentialsException e) {
            log.warn("Login failed - bad credentials: {}", request.getPhone());
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Số điện thoại hoặc mật khẩu không đúng!");
        } catch (LockedException e) {
            log.warn("Login failed - account locked: {}", request.getPhone());
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản đã bị khóa. Vui lòng liên hệ Admin.");
        } catch (DisabledException e) {
            log.warn("Login failed - account disabled: {}", request.getPhone());
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa.");
        } catch (AuthenticationException e) {
            log.warn("Login failed - cannot authenticate {}: {}", request.getPhone(), e.getMessage());
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Số điện thoại hoặc mật khẩu không đúng!");
        }
    }

    @Override
    @Transactional
    public SendOtpResponse sendRegisterOtp(SendOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.CONFLICT, "Số điện thoại đã tồn tại trong hệ thống!");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.CONFLICT, "Email đã tồn tại trong hệ thống!");
        }
        return sendEmailOtp(email, OtpPurposeEnum.REGISTER);
    }

    @Override
    @Transactional
    public SendOtpResponse sendForgotPasswordOtp(ForgotPasswordSendOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        // Never disclose whether an account exists in this public response.
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) return otpResponse();
        return sendEmailOtp(email, OtpPurposeEnum.RESET_PASSWORD);
    }

    private SendOtpResponse sendEmailOtp(String email, OtpPurposeEnum purpose) {
        LocalDateTime now = LocalDateTime.now();
        OtpChallenge challenge = otpChallengeRepository.findByRecipientAndPurpose(email, purpose)
                .orElseGet(() -> OtpChallenge.builder().recipient(email).purpose(purpose).build());
        if (challenge.getSentAt() != null && now.isBefore(challenge.getSentAt().plusSeconds(60))) {
            if (purpose == OtpPurposeEnum.RESET_PASSWORD) return otpResponse();
            throw new AppException(ErrorCode.CONFLICT, "Vui lòng chờ 60 giây trước khi gửi lại mã.");
        }
        String rawOtp = generateNumericOtp(OTP_LENGTH);
        challenge.setCodeHash(passwordEncoder.encode(rawOtp));
        challenge.setExpiresAt(now.plusSeconds(OTP_TTL_SECONDS));
        challenge.setSentAt(now);
        challenge.setConsumedAt(null);
        challenge.setAttemptCount(0);
        // Flush unique recipient/purpose before delivery: concurrent first requests cannot both send.
        otpChallengeRepository.saveAndFlush(challenge);
        otpMailSender.send(email, rawOtp, purpose);
        return otpResponse();
    }

    private SendOtpResponse otpResponse() {
        return SendOtpResponse.builder().expiresInSeconds(OTP_TTL_SECONDS).resendAfterSeconds(60).build();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || email.trim().length() > 100) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Email nhận mã không hợp lệ.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(noRollbackFor = OtpRejectedException.class)
    public void resetPasswordWithOtp(ForgotPasswordResetRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Email không hợp lệ hoặc không tồn tại!"));

        verifyAndConsumeOtp(email, request.getOtpCode(), OtpPurposeEnum.RESET_PASSWORD);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("User {} reset password successfully via OTP", email);
    }

    @Override
    @Transactional(noRollbackFor = OtpRejectedException.class)
    public void register(RegisterRequest request) {
        if (request.getRole() == RoleEnum.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không được phép tự đăng ký tài khoản Quản trị viên (ADMIN)!");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.CONFLICT, "Số điện thoại đã tồn tại trong hệ thống!");
        }
        if (userRepository.existsByEmailIgnoreCase(normalizeEmail(request.getEmail()))) {
            throw new AppException(ErrorCode.CONFLICT, "Email đã tồn tại trong hệ thống!");
        }

        verifyAndConsumeOtp(normalizeEmail(request.getEmail()), request.getOtpCode(), OtpPurposeEnum.REGISTER);

        User user = User.builder()
                .phone(request.getPhone())
                .email(normalizeEmail(request.getEmail()))
                .emailVerifiedAt(LocalDateTime.now())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatusEnum.ACTIVE)
                .build();
        User savedUser = userRepository.save(user);

        if (request.getRole() == RoleEnum.DRIVER) {
            if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Họ và tên là bắt buộc đối với Tài xế!");
            }
            Driver driver = Driver.builder()
                    .user(savedUser)
                    .fullName(request.getFullName().trim())
                    .ratingAverage(new BigDecimal("5.00"))
                    .build();
            driverRepository.save(driver);
        } else if (request.getRole() == RoleEnum.MERCHANT) {
            if (request.getCitizenId() == null || request.getCitizenId().trim().isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "CCCD/CMND là bắt buộc đối với Chủ bãi xe!");
            }
            if (request.getBankName() == null || request.getBankName().trim().isEmpty() ||
                request.getBankAccountNum() == null || request.getBankAccountNum().trim().isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Thông tin tài khoản ngân hàng là bắt buộc đối với Chủ bãi xe!");
            }
            Merchant merchant = Merchant.builder()
                    .user(savedUser)
                    .citizenId(request.getCitizenId().trim())
                    .bankName(request.getBankName().trim())
                    .bankAccountNum(request.getBankAccountNum().trim())
                    .walletBalance(BigDecimal.ZERO)
                    .build();
            merchantRepository.save(merchant);
        }

        log.info("User registered successfully: phone={}, role={}", savedUser.getPhone(), savedUser.getRole());
    }

    @Override
    @Transactional(noRollbackFor = OtpRejectedException.class)
    public void verifyRegisterOtp(VerifyRegisterOtpRequest request) {
        verifyOtp(normalizeEmail(request.getEmail()), request.getOtpCode(), OtpPurposeEnum.REGISTER);
    }

    private void verifyAndConsumeOtp(String identifier, String rawOtp, OtpPurposeEnum purpose) {
        OtpChallenge challenge = verifyOtp(identifier, rawOtp, purpose);
        challenge.setConsumedAt(LocalDateTime.now());
        otpChallengeRepository.save(challenge);
    }

    private OtpChallenge verifyOtp(String identifier, String rawOtp, OtpPurposeEnum purpose) {
        OtpChallenge challenge = otpChallengeRepository.findByRecipientAndPurpose(identifier, purpose)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Mã OTP không tồn tại hoặc đã hết hạn!"));

        if (challenge.getConsumedAt() != null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã OTP đã được sử dụng!");
        }

        if (!LocalDateTime.now().isBefore(challenge.getExpiresAt())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã OTP đã hết hạn!");
        }

        if (challenge.getAttemptCount() >= OTP_MAX_ATTEMPTS) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã OTP đã vượt quá số lần thử cho phép!");
        }

        if (!passwordEncoder.matches(rawOtp, challenge.getCodeHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            otpChallengeRepository.save(challenge);
            throw new OtpRejectedException();
        }

        return challenge;
    }

    private String generateNumericOtp(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
