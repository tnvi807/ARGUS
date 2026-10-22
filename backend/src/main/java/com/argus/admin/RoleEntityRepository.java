package com.argus.admin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleEntityRepository extends JpaRepository<RoleEntity, String> {

    Optional<RoleEntity> findByName(String name);
}
