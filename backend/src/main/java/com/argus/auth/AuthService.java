package com.argus.auth;

import com.argus.audit.AuditWriter;
import com.argus.common.ApiErrorException;
import com.argus.config.AppProperties;
import com.argus.security.JwtService;
import com.argus.security.Permissions;
import com.argus.user.AppUser;
import com.argus.user.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Secure administrator authentication: BCrypt verification, generic failure messaging (no user
 * enumeration), progressive account lockout, database-backed token revocation, and audit trail
 * entries that record outcomes without ever recording credentials.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final JwtService jwtService;
    private final AppProperties props;
    private final AuditWriter auditWriter;
    private final com.argus.admin.PermissionRegistry permissionRegistry;
    private final TransactionTemplate transactionTemplate;

    public AuthService(AuthenticationManager authenticationManager, AppUserRepository userRepository,
                       JwtService jwtService, AppProperties props, AuditWriter auditWriter,
                       com.argus.admin.PermissionRegistry permissionRegistry,
                       PlatformTransactionManager transactionManager) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.props = props;
        this.auditWriter = auditWriter;
        this.permissionRegistry = permissionRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, HttpServletRequest http) {
        String username = request.username() == null ? "" : request.username().trim();
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
            AppUser user = userRepository.findByUsername(auth.getName())
                    .orElseThrow(() -> new BadCredentialsException("no user"));
            if (user.isLocked()) {
                throw ApiErrorException.unauthorized("Account temporarily locked after repeated failures");
            }
            if (!user.isEnabled()) {
                throw ApiErrorException.unauthorized("Account disabled");
            }

            return transactionTemplate.execute(status -> {
                user.resetFailures();
                user.setLastLoginAt(Instant.now());
                userRepository.save(user);

                String token = jwtService.issue(user);
                auditWriter.record("auth", "LOGIN", "Session opened for " + user.getUsername(),
                        "ALLOWED", 0, user.getUsername(), clientIp(http));
                return new AuthDtos.LoginResponse(token, jwtService.ttlSeconds(),
                        AuthDtos.UserView.of(user, permissionRegistry.permissionsFor(user.getRoles())));
            });
        } catch (DisabledException ex) {
            rejectAndAudit(username, "disabled account", http);
            throw ApiErrorException.unauthorized("Account disabled");
        } catch (LockedException ex) {
            rejectAndAudit(username, "locked account", http);
            throw ApiErrorException.unauthorized("Account temporarily locked after repeated failures");
        } catch (AuthenticationException ex) {
            registerFailureAndAudit(username, http);
            // Deliberately generic: do not reveal whether the account exists.
            throw ApiErrorException.unauthorized("Invalid username or password");
        }
    }

    /**
     * The failure counter and the rejection audit are committed in their own REQUIRES_NEW
     * transaction so they survive the ApiErrorException rollback of the enclosing request.
     */
    private void registerFailureAndAudit(String username, HttpServletRequest http) {
        if (username == null || username.isBlank()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            registerFailure(username);
            auditWriter.record("auth", "LOGIN_REJECTED", "Sign-in rejected for " + username,
                    "BLOCKED", 100, username, clientIp(http));
        });
    }

    private void rejectAndAudit(String username, String reason, HttpServletRequest http) {
        if (username == null || username.isBlank()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                auditWriter.record("auth", "LOGIN_REJECTED",
                        "Sign-in rejected (" + reason + ") for " + username,
                        "BLOCKED", 100, username, clientIp(http)));
    }

    private void registerFailure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        Optional<AppUser> userOpt = userRepository.findByUsername(username);
        userOpt.ifPresent(user -> {
            user.registerFailure(props.getSecurity().getMaxFailedAttempts(),
                    props.getSecurity().getLockoutMinutes());
            userRepository.save(user);
        });
    }

    @Transactional
    public void logout(HttpServletRequest http) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return;
        }
        String username = auth.getName();
        userRepository.findByUsername(username).ifPresent(user -> {
            user.revokeTokens();
            userRepository.save(user);
        });
        SecurityContextHolder.clearContext();
        auditWriter.record("auth", "LOGOUT", "Session closed for " + username, "ALLOWED", 0, username, clientIp(http));
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserView me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw ApiErrorException.unauthorized("Authentication required");
        }
        AppUser user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> ApiErrorException.unauthorized("Authentication required"));
        return AuthDtos.UserView.of(user, permissionRegistry.permissionsFor(user.getRoles()));
    }

    private String clientIp(HttpServletRequest http) {
        if (http == null) {
            return null;
        }
        return http.getRemoteAddr();
    }
}
