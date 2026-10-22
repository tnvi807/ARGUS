package com.argus.admin;

import com.argus.auth.AuthDtos;
import com.argus.common.ApiErrorException;
import com.argus.config.AppProperties;
import com.argus.security.Permissions;
import com.argus.user.AppUser;
import com.argus.user.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User, role and permission administration (ADMIN only). */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
public class AdminUserController {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    public record CreateUserRequest(
            @NotBlank(message = "username is required")
            @Size(min = 3, max = 32, message = "username must be 3-32 characters")
            String username,
            @NotBlank(message = "password is required")
            @Size(min = 12, max = 128, message = "password must be at least 12 characters")
            String password,
            @Size(max = 80) String displayName,
            @NotEmpty(message = "at least one role is required")
            Set<String> roles) {
    }

    public record UpdateUserRequest(
            @Size(max = 80) String displayName,
            Set<String> roles,
            Boolean enabled) {
    }

    public record PasswordRequest(
            @NotBlank(message = "password is required")
            @Size(min = 12, max = 128, message = "password must be at least 12 characters")
            String password) {
    }

    public record UserView(Long id, String username, String displayName, Set<String> roles,
                           boolean enabled, int failedAttempts, boolean locked, int tokenVersion,
                           Instant createdAt, Instant lastLoginAt) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(),
                    Set.copyOf(u.getRoles()), u.isEnabled(), u.getFailedAttempts(), u.isLocked(),
                    u.getTokenVersion(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRegistry permissionRegistry;
    private final PermissionEntityRepository permissionRepository;
    private final AppProperties props;
    private final com.argus.audit.AuditWriter auditWriter;

    public AdminUserController(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                               PermissionRegistry permissionRegistry,
                               PermissionEntityRepository permissionRepository,
                               AppProperties props,
                               com.argus.audit.AuditWriter auditWriter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionRegistry = permissionRegistry;
        this.permissionRepository = permissionRepository;
        this.props = props;
        this.auditWriter = auditWriter;
    }

    @GetMapping("/users")
    public List<UserView> users() {
        return userRepository.findAll().stream().map(UserView::of).toList();
    }

    @PostMapping("/users")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        String username = request.username().trim().toLowerCase();
        if (!USERNAME.matcher(username).matches()) {
            throw ApiErrorException.badRequest(
                    "Username must be lowercase letters, digits, dot, underscore or dash (3-32 chars)");
        }
        if (userRepository.existsByUsername(username)) {
            throw ApiErrorException.badRequest("Username already exists");
        }
        validatePasswordStrength(request.password());
        validateRoles(request.roles());

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? username : request.displayName().trim());
        user.setRoles(new LinkedHashSet<>(lowercaseRoles(request.roles())));
        user.setEnabled(true);
        AppUser saved = userRepository.save(user);
        auditWriter.record("admin", "user-created",
                "Created user '" + saved.getUsername() + "' with roles " + saved.getRoles(),
                "ALLOWED", 0, currentActor(), saved.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserView.of(saved));
    }

    @PutMapping("/users/{id}")
    @org.springframework.transaction.annotation.Transactional
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        AppUser user = require(id);
        String self = currentActor();
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.roles() != null && !request.roles().isEmpty()) {
            validateRoles(request.roles());
            Set<String> next = new LinkedHashSet<>(lowercaseRoles(request.roles()));
            boolean losingAdmin = user.getRoles().contains(Permissions.ROLE_ADMIN)
                    && !next.contains(Permissions.ROLE_ADMIN);
            if (losingAdmin && user.getUsername().equals(self)) {
                throw ApiErrorException.badRequest("You cannot remove your own ADMIN role");
            }
            if (losingAdmin) {
                ensureAnotherEnabledAdmin(user);
            }
            user.setRoles(next);
            user.revokeTokens();
        }
        if (request.enabled() != null) {
            if (!request.enabled() && user.getUsername().equals(self)) {
                throw ApiErrorException.badRequest("You cannot disable your own account");
            }
            if (!request.enabled() && user.getRoles().contains(Permissions.ROLE_ADMIN)) {
                ensureAnotherEnabledAdmin(user);
            }
            user.setEnabled(request.enabled());
            user.revokeTokens();
        }
        AppUser saved = userRepository.save(user);
        auditWriter.record("admin", "user-updated",
                "Updated user '" + saved.getUsername() + "' roles=" + saved.getRoles()
                        + " enabled=" + saved.isEnabled(),
                "ALLOWED", 0, self, saved.getUsername());
        return UserView.of(saved);
    }

    @PostMapping("/users/{id}/password")
    @org.springframework.transaction.annotation.Transactional
    public UserView changePassword(@PathVariable Long id, @Valid @RequestBody PasswordRequest request) {
        AppUser user = require(id);
        validatePasswordStrength(request.password());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.revokeTokens();
        user.resetFailures();
        AppUser saved = userRepository.save(user);
        auditWriter.record("admin", "password-reset",
                "Password reset for user '" + saved.getUsername() + "' (all sessions revoked)",
                "ALLOWED", 0, currentActor(), saved.getUsername());
        return UserView.of(saved);
    }

    @PostMapping("/users/{id}/unlock")
    @org.springframework.transaction.annotation.Transactional
    public UserView unlock(@PathVariable Long id) {
        AppUser user = require(id);
        user.resetFailures();
        AppUser saved = userRepository.save(user);
        auditWriter.record("admin", "user-unlocked",
                "Cleared lockout for user '" + saved.getUsername() + "'", "ALLOWED", 0,
                currentActor(), saved.getUsername());
        return UserView.of(saved);
    }

    @GetMapping("/roles")
    public java.util.Map<String, PermissionRegistry.RoleView> roles() {
        return permissionRegistry.roles();
    }

    @GetMapping("/permissions")
    public List<PermissionEntity> permissions() {
        return permissionRepository.findAll();
    }

    private void ensureAnotherEnabledAdmin(AppUser target) {
        boolean other = userRepository.findAll().stream()
                .anyMatch(u -> !u.getId().equals(target.getId())
                        && u.isEnabled()
                        && u.getRoles().contains(Permissions.ROLE_ADMIN));
        if (!other) {
            throw ApiErrorException.badRequest("At least one enabled ADMIN account must remain");
        }
    }

    private void validateRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw ApiErrorException.badRequest("At least one role is required");
        }
        for (String role : roles) {
            if (!Permissions.isKnownRole(role)) {
                throw ApiErrorException.badRequest("Unknown role: " + role);
            }
        }
    }

    private List<String> lowercaseRoles(Set<String> roles) {
        List<String> out = new ArrayList<>();
        for (String role : roles) {
            out.add(role.toUpperCase());
        }
        return out;
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 12) {
            throw ApiErrorException.badRequest("Password must be at least 12 characters");
        }
        if (!HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find()) {
            throw ApiErrorException.badRequest("Password must contain at least a letter and a digit");
        }
    }

    private AppUser require(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiErrorException.notFound("User"));
    }

    private String currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "unknown" : auth.getName();
    }
}
