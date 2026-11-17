package com.smartparking.payment.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalAuthFilter internalAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth.requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                    .requestMatchers("/api/v1/payments/webhook").permitAll()
                    .requestMatchers("/api/v1/internal/**").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/merchant/**").hasRole("MERCHANT")
                    .requestMatchers("/api/v1/withdrawals/**").hasRole("MERCHANT")
                    .requestMatchers("/api/v1/disputes/me", "/api/v1/disputes/me/**").hasRole("DRIVER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/disputes/evidence").hasRole("DRIVER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/disputes/evidence/**").hasAnyRole("DRIVER", "MERCHANT", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/disputes").hasRole("DRIVER")
                    .requestMatchers("/api/v1/disputes/merchant").hasRole("MERCHANT")
                    .requestMatchers(HttpMethod.POST, "/api/v1/disputes/*/responses").hasRole("MERCHANT")
                    .requestMatchers("/api/v1/disputes/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/disputes/*/resolve").hasRole("ADMIN")
                    .requestMatchers("/api/v1/driver/wallet", "/api/v1/driver/wallet/**").hasRole("DRIVER")
                    .requestMatchers("/api/v1/payments/gate/**").hasRole("MERCHANT")
                    .requestMatchers("/api/v1/payments/**").hasRole("DRIVER")
                    .requestMatchers("/api/v1/disputes/**").authenticated()
                    .anyRequest().authenticated()
            );

        http.addFilterBefore(internalAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
