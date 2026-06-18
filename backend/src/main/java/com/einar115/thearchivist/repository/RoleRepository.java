package com.einar115.thearchivist.repository;

import com.einar115.thearchivist.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, Integer> {
}
