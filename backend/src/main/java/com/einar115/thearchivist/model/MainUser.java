package com.einar115.thearchivist.model;

import com.einar115.thearchivist.entity.UserEntity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class MainUser implements UserDetails, CredentialsContainer, Serializable {
    private final Integer id;
    private final String username;
    private String password;
    private final boolean enabled;
    private final List<String> roles;

    private MainUser(Integer id, String username, String password, boolean enabled, List<String> roles) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.roles = List.copyOf(roles);
    }

    public static MainUser from(UserEntity entity) {
        return new MainUser(
                entity.getId(),
                entity.getUsername(),
                entity.getPassword(),
                entity.isEnabled(),
                entity.getRoles().stream()
                        .map(role -> role.getName().name())
                        .toList()
        );
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public @NonNull String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    public Integer getId() {
        return id;
    }

    public List<String> getRoles() {
        return roles;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MainUser other && username.equals(other.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "MainUser[username=%s, roles=%s]".formatted(username, roles);
    }

}
