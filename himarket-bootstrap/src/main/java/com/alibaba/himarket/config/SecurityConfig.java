/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.alibaba.himarket.config;

import com.alibaba.himarket.core.security.JwtAuthenticationFilter;
import com.alibaba.himarket.service.idp.session.AuthSessionStore;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthSessionStore authSessionStore;

    // Auth endpoints
    private static final String[] AUTH_WHITELIST = {
        "/auth/**",
        "/idp/**",
        "/login",
        "/register",
        "/admins/init",
        "/admins/need-init",
        "/admins/login",
        "/admins/logout",
        "/developers/cas/**",
        "/developers/ldap/**",
        "/developers/oidc/**",
        "/developers/oauth2/**",
        "/admins/cas/**",
        "/admins/ldap/**",
        "/api/v1/admins/init",
        "/api/v1/admins/need-init",
        "/api/v1/admins/login",
        "/api/v1/developers/cas/**",
        "/api/v1/developers/ldap/**",
        "/api/v1/developers/oidc/**",
        "/api/v1/developers/oauth2/**",
        "/api/v1/admins/cas/**",
        "/api/v1/admins/ldap/**"
    };

    // Swagger endpoints
    private static final String[] SWAGGER_WHITELIST = {
        "/portal/swagger-ui.html", "/portal/swagger-ui/**", "/portal/v3/api-docs/**"
    };

    // System endpoints
    private static final String[] SYSTEM_WHITELIST = {"/favicon.ico", "/error"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Permit async dispatch for SSE/streaming
                                        .dispatcherTypeMatchers(DispatcherType.ASYNC)
                                        .permitAll()
                                        // Permit OPTIONS
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        // Permit developer registration (POST /developers)
                                        .requestMatchers(HttpMethod.POST, "/developers")
                                        .permitAll()
                                        // Permit all auth related paths
                                        .requestMatchers(AUTH_WHITELIST)
                                        .permitAll()
                                        // Permit Swagger endpoints
                                        .requestMatchers(SWAGGER_WHITELIST)
                                        .permitAll()
                                        // Permit system endpoints
                                        .requestMatchers(SYSTEM_WHITELIST)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(authSessionStore),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(Arrays.asList("*"));
        corsConfig.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        corsConfig.setAllowedHeaders(Arrays.asList("*"));
        corsConfig.setExposedHeaders(Arrays.asList("Authorization"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}
