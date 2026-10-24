package com.smartparking.identity.infrastructure.security;

import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("loadUserByUsername returns UserDetails when user exists")
    void testLoadUserByUsernameSuccess() {
        User user = User.builder()
                .id(1L)
                .phone("0987654321")
                .passwordHash("hashed_password")
                .role(RoleEnum.DRIVER)
                .status(UserStatusEnum.ACTIVE)
                .build();

        when(userRepository.findByPhone("0987654321")).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("0987654321");

        assertNotNull(userDetails);
        assertEquals("0987654321", userDetails.getUsername());
        assertTrue(userDetails.isEnabled());
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException when user does not exist")
    void testLoadUserByUsernameNotFound() {
        when(userRepository.findByPhone("0999999999")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("0999999999"));
    }

    @Test
    @DisplayName("loadUserById returns UserDetails when user exists")
    void testLoadUserByIdSuccess() {
        User user = User.builder()
                .id(2L)
                .phone("0123456789")
                .passwordHash("secret")
                .role(RoleEnum.MERCHANT)
                .status(UserStatusEnum.ACTIVE)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserById(2L);

        assertNotNull(userDetails);
        assertEquals("0123456789", userDetails.getUsername());
    }

    @Test
    @DisplayName("loadUserById throws AppException when user does not exist")
    void testLoadUserByIdNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () ->
                userDetailsService.loadUserById(999L));
    }
}
