package com.argus.admin;

import com.argus.security.Permissions;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves role -&gt; permission grants from the database (seeded from the static least-privilege
 * catalogue). Unknown roles contribute no permissions.
 */
@Component
public class PermissionRegistry {

    private final RoleEntityRepository roleRepository;

    public PermissionRegistry(RoleEntityRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public Set<String> permissionsFor(Set<String> roles) {
        Set<String> out = new LinkedHashSet<>();
        if (roles == null || roles.isEmpty()) {
            return out;
        }
        List<RoleEntity> all = roleRepository.findAll();
        for (RoleEntity role : all) {
            if (roles.contains(role.getName())) {
                out.addAll(role.getPermissions());
            }
        }
        if (out.isEmpty()) {
            // Fall back to the static catalogue if roles have not been seeded yet.
            return Permissions.forRoles(roles);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, RoleView> roles() {
        Map<String, RoleView> out = new LinkedHashMap<>();
        for (RoleEntity role : roleRepository.findAll()) {
            out.put(role.getName(),
                    new RoleView(role.getName(), role.getDescription(), role.getPermissions()));
        }
        return out;
    }

    public record RoleView(String name, String description, Set<String> permissions) {
    }
}
