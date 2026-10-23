package com.smartparking.booking.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                    .requestMatchers("/api/v1/internal/**").permitAll()
                    .requestMatchers("/api/v1/reviews/parking-lot/**").permitAll()
                    .requestMatchers("/api/v1/merchant/**").hasRole("MERCHANT")
                    .requestMatchers("/api/v1/bookings/**").authenticated()
                    .requestMatchers("/api/v1/queues/**").authenticated()
                    .requestMatchers("/api/v1/reviews/**").authenticated()
                    .anyRequest().authenticated()
            );

        http.addFilterBefore(internalAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
