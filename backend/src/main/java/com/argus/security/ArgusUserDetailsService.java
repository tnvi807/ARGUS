package com.argus.security;

import com.argus.admin.PermissionRegistry;
import com.argus.user.AppUser;
import com.argus.user.AppUserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads local administrator accounts; authorities come from the database role -&gt; permission map. */
@Service
public class ArgusUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final PermissionRegistry permissionRegistry;

    public ArgusUserDetailsService(AppUserRepository userRepository, PermissionRegistry permissionRegistry) {
        this.userRepository = userRepository;
        this.permissionRegistry = permissionRegistry;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        permissionRegistry.permissionsFor(user.getRoles())
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        user.getRoles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}
