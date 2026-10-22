package com.argus.security;

import com.argus.config.AppProperties;
import com.argus.user.AppUser;
import com.argus.user.AppUserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Issues and validates short-lived, revocable HS256 JWTs.
 *
 * <p>Revocation is database-backed: every token carries a {@code tv} (token version) claim that is
 * checked against the stored user on every request. Logging out or changing a user's roles bumps the
 * version and instantly invalidates every outstanding token for that user without a server session.
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    static final String CLAIM_TOKEN_VERSION = "tv";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_PERMS = "perms";

    private final AppProperties props;
    private final AppUserRepository userRepository;
    private final com.argus.admin.PermissionRegistry permissionRegistry;
    private final SecretKeySpec secretKey;

    public JwtService(AppProperties props, AppUserRepository userRepository,
                      com.argus.admin.PermissionRegistry permissionRegistry) {
        this.props = props;
        this.userRepository = userRepository;
        this.permissionRegistry = permissionRegistry;
        byte[] key = props.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException("ARGUS_JWT_SECRET must be at least 32 bytes (256 bits) for HS256");
        }
        this.secretKey = new SecretKeySpec(key, "HmacSHA256");
    }

    @PostConstruct
    void warnIfDefaultSecret() {
        if (props.getSecurity().getJwtSecret().startsWith("argus-dev-only")) {
            log.warn("ARGUS is running with the built-in development JWT secret. "
                    + "Set the ARGUS_JWT_SECRET environment variable before deploying.");
        }
    }

    public String issue(AppUser user) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(props.getSecurity().getJwtTtlMinutes() * 60);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getUsername())
                    .claim("name", user.getDisplayName())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .claim(CLAIM_ROLES, new ArrayList<>(user.getRoles()))
                    .claim(CLAIM_PERMS, new ArrayList<>(permissionRegistry.permissionsFor(user.getRoles())))
                    .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims);
            jwt.sign(new MACSigner(secretKey.getEncoded()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign token", e);
        }
    }

    public JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        return token -> {
            Jwt jwt;
            try {
                jwt = decoder.decode(token);
            } catch (JwtException e) {
                throw e;
            }
            validateStillValid(jwt);
            return jwt;
        };
    }

    /**
     * Rejects tokens issued before a logout/role change (token version mismatch) and tokens for
     * disabled or deleted accounts. Password hashes and secrets are never placed in claims.
     */
    private void validateStillValid(Jwt jwt) {
        Optional<AppUser> userOpt = userRepository.findByUsername(jwt.getSubject());
        if (userOpt.isEmpty()) {
            throw new InvalidBearerTokenException("Token subject no longer exists");
        }
        AppUser user = userOpt.get();
        if (!user.isEnabled()) {
            throw new InvalidBearerTokenException("Account is disabled");
        }
        long claimed = jwt.getClaimAsString(CLAIM_TOKEN_VERSION) == null
                ? -1
                : Long.parseLong(jwt.getClaimAsString(CLAIM_TOKEN_VERSION));
        if (claimed != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Token has been revoked");
        }
    }

    public List<GrantedAuthority> authoritiesFor(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object perms = jwt.getClaim(CLAIM_PERMS);
        if (perms instanceof List<?> list) {
            list.forEach(p -> authorities.add(new SimpleGrantedAuthority(String.valueOf(p))));
        }
        Object roles = jwt.getClaim(CLAIM_ROLES);
        if (roles instanceof List<?> list) {
            list.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        }
        return authorities;
    }

    public long ttlSeconds() {
        return props.getSecurity().getJwtTtlMinutes() * 60L;
    }

}
