package com.einar115.thearchivist.model;

import com.einar115.thearchivist.entity.UserEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

public record MainUser(UserEntity userEntity) implements UserDetails {
    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return userEntity.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return userEntity.getPassword();
    }

    @Override
    public @NonNull String getUsername() {
        return userEntity.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return userEntity.isEnabled();
    }
    
    public Integer getId() {
        return userEntity.getId();
    }
}
