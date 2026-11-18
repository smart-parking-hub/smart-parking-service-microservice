package com.smartparking.parkinglot.infrastructure.security;

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
                    .requestMatchers("/api/v1/internal/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/search").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/{id}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/{id}/slots").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/{id}/availability", "/api/v1/parking-lots/{id}/timeline").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/{id}/pricing").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/{id}/layout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/{id}/layout/background").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/{id}/layout/availability").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/parking-lots/{parkingLotId}/images/**").permitAll()
                    .requestMatchers("/api/v1/merchant/**").hasRole("MERCHANT")
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            );

        http.addFilterBefore(internalAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
