/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.config;

import com.alibaba.himarket.core.security.JwtAuthenticationFilter;
import com.alibaba.himarket.core.security.PublicAccessPathScanner;
import com.alibaba.himarket.core.security.PublicAccessPathScanner.PublicAccessEndpoint;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final PublicAccessPathScanner publicAccessPathScanner;

    private static final RequestMatcher[] AUTH_WHITELIST =
            antMatchers(
                    "/admins/init",
                    "/admins/need-init",
                    "/admins/login",
                    "/admins/cas/authorize",
                    "/admins/cas/callback",
                    "/admins/cas/providers",
                    "/admins/cas/logout",
                    "/**/admins/cas/authorize",
                    "/**/admins/cas/callback",
                    "/**/admins/cas/providers",
                    "/**/admins/cas/logout",
                    "/developers/login",
                    "/**/developers/login",
                    "/developers/authorize",
                    "/**/developers/authorize",
                    "/developers/callback",
                    "/**/developers/callback",
                    "/developers/providers",
                    "/**/developers/providers",
                    "/developers/oidc/authorize",
                    "/developers/oidc/callback",
                    "/developers/oidc/providers",
                    "/**/developers/oidc/authorize",
                    "/**/developers/oidc/callback",
                    "/**/developers/oidc/providers",
                    "/developers/cas/authorize",
                    "/developers/cas/callback",
                    "/developers/cas/providers",
                    "/developers/cas/logout",
                    "/**/developers/cas/authorize",
                    "/**/developers/cas/callback",
                    "/**/developers/cas/providers",
                    "/**/developers/cas/logout",
                    "/developers/oauth2/token",
                    "/**/developers/oauth2/token",
                    "/ws/acp",
                    "/ws/terminal",
                    "/cli-providers",
                    "/skills/*/download",
                    "/workers/*/download",
                    "/workers/*/files/**");

    private static final RequestMatcher[] SWAGGER_WHITELIST =
            antMatchers(
                    "/portal/swagger-ui.html", "/portal/swagger-ui/**", "/portal/v3/api-docs/**");

    private static final RequestMatcher[] SYSTEM_WHITELIST = antMatchers("/favicon.ico", "/error");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        List<PublicAccessEndpoint> publicEndpoints =
                publicAccessPathScanner.getPublicAccessEndpoints();
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> {
                            auth.dispatcherTypeMatchers(DispatcherType.ASYNC)
                                    .permitAll()
                                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                                    .permitAll()
                                    .requestMatchers(HttpMethod.POST, "/developers")
                                    .permitAll()
                                    .requestMatchers(AUTH_WHITELIST)
                                    .permitAll()
                                    .requestMatchers(SWAGGER_WHITELIST)
                                    .permitAll()
                                    .requestMatchers(SYSTEM_WHITELIST)
                                    .permitAll();
                            for (PublicAccessEndpoint endpoint : publicEndpoints) {
                                if (endpoint.httpMethod() != null) {
                                    auth.requestMatchers(endpoint.httpMethod(), endpoint.path())
                                            .permitAll();
                                } else {
                                    auth.requestMatchers(endpoint.path()).permitAll();
                                }
                            }
                            auth.anyRequest().authenticated();
                        })
                .addFilterBefore(
                        new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static RequestMatcher[] antMatchers(String... patterns) {
        return Arrays.stream(patterns)
                .map(AntPathRequestMatcher::new)
                .toArray(RequestMatcher[]::new);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(Collections.singletonList("*"));
        corsConfig.setAllowedMethods(Collections.singletonList("*"));
        corsConfig.setAllowedHeaders(Collections.singletonList("*"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}
