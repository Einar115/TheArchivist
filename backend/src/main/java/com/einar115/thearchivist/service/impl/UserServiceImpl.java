package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.dto.request.UserRequest;
import com.einar115.thearchivist.dto.response.UserResponse;
import com.einar115.thearchivist.entity.RoleEntity;
import com.einar115.thearchivist.entity.UserEntity;
import com.einar115.thearchivist.exception.UsernameAlreadyExistsException;
import com.einar115.thearchivist.repository.RoleRepository;
import com.einar115.thearchivist.repository.UserRepository;
import com.einar115.thearchivist.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserResponse createUser(UserRequest userRequest) {
        if (userRepository.findByUsername(userRequest.username()).isPresent()) {
            throw new UsernameAlreadyExistsException(userRequest.username());
        }

        RoleEntity role = roleRepository.findByName(userRequest.role())
                .orElseThrow(() -> new IllegalStateException(
                        "Role not found in database: " + userRequest.role()));

        UserEntity user = new UserEntity();
        user.setUsername(userRequest.username());
        user.setPassword(passwordEncoder.encode(userRequest.password()));
        user.setEnabled(true);
        user.setRoles(new ArrayList<>(List.of(role)));

        UserEntity saved = userRepository.save(user);

        return new UserResponse(saved.getId(), saved.getUsername(), role.getName());
    }

    @Override
    public void changePassword(Integer userId, String newPassword) {
        throw new UnsupportedOperationException("no implemented yet");
    }
}
