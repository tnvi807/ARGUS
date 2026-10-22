package com.argus.config;

import com.argus.common.ApiError;
import com.argus.security.JwtService;
import com.argus.security.Permissions;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT resource-server security.
 *
 * <ul>
 *   <li>Everything under {@code /api/**} requires a valid bearer token except login and health.</li>
 *   <li>Role/permission checks are enforced per method via {@code @PreAuthorize} (least privilege).</li>
 *   <li>401/403 are returned as structured JSON, never as an HTML error page or stack trace.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtService jwtService, AppProperties props, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> {
                        })
                        .xssProtection(x -> x.headerValue(
                                org.springframework.security.web.header.writers
                                        .XXssProtectionHeaderWriter.HeaderValue.ENABLED))
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/actuator/health", "/actuator/info",
                                "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority(Permissions.ADMIN_MANAGE)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtService.decoder())
                                .jwtAuthenticationConverter(new ArgusJwtAuthenticationConverter(jwtService)))
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint()))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            com.argus.security.ArgusUserDetailsService userDetailsService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        org.springframework.security.authentication.dao.DaoAuthenticationProvider provider =
                new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    /** BCrypt with the delegating {bcrypt} prefix so hashes stay re-hashable later. */
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return org.springframework.security.crypto.factory.PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.getCors().getAllowedOrigins());
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Requested-With"));
        config.setExposedHeaders(java.util.List.of("Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) ->
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated",
                        "Authentication required", request);
    }

    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, ex) -> writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                "access_denied", "You do not have permission to perform this action", request);
    }

    private void writeJson(HttpServletResponse response, int status, String code, String message,
                           HttpServletRequest request) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiError.of(status, code, message, request.getRequestURI()));
        SecurityContextHolder.clearContext();
    }
}
