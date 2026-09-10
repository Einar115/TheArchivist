package com.einar115.thearchivist.service;

import com.einar115.thearchivist.entity.UserEntity;

public interface UserService {
    UserEntity createUser(UserEntity user);
    UserEntity changePassword(String newPassword);
}
