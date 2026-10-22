package com.argus.config;

import com.argus.security.JwtService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Maps JWT claims to granted authorities (permissions + ROLE_* prefixed roles). */
public class ArgusJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtService jwtService;

    public ArgusJwtAuthenticationConverter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(jwtService.authoritiesFor(jwt));
        authorities.add(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
