package com.einar115.thearchivist.repository;

import com.einar115.thearchivist.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Integer> {
    Optional<RoleEntity> findByName(RoleEntity.RoleEnum name);
}
