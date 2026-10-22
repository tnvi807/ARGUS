package com.argus.auth;

import com.argus.security.Permissions;
import com.argus.user.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "username is required")
            @Size(max = 64, message = "username is too long")
            String username,
            @NotBlank(message = "password is required")
            @Size(max = 128, message = "password is too long")
            String password) {
    }

    public record UserView(Long id, String username, String displayName, Set<String> roles,
                           Set<String> permissions) {
        public static UserView of(AppUser user) {
            return of(user, Permissions.forRoles(user.getRoles()));
        }

        public static UserView of(AppUser user, Set<String> permissions) {
            Set<String> roles = user.getRoles() == null
                    ? Set.of()
                    : Set.copyOf(new LinkedHashSet<>(user.getRoles()));
            return new UserView(user.getId(), user.getUsername(), user.getDisplayName(),
                    roles, permissions);
        }
    }

    public record LoginResponse(String token, long expiresInSeconds, UserView user) {
    }
}
