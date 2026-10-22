package com.argus.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One permission code (e.g. {@code alerts.act}) with a human description. */
@Entity
@Table(name = "permissions")
public class PermissionEntity {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String description;

    protected PermissionEntity() {
    }

    public PermissionEntity(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
