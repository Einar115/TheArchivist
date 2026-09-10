package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.entity.UserEntity;
import com.einar115.thearchivist.repository.UserRepository;
import com.einar115.thearchivist.service.UserService;

public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    @Override
    public UserEntity createUser(UserEntity user) {
        return userRepository.save(user);
    }

    //not implemented yet
    @Override
    public UserEntity changePassword(String newPassword) {
        return null;
    }
}
