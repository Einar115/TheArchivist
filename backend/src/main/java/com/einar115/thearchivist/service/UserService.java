package com.einar115.thearchivist.service;

import com.einar115.thearchivist.dto.request.UserRequest;
import com.einar115.thearchivist.dto.response.UserResponse;

public interface UserService {

    UserResponse createUser(UserRequest userRequest);

    void changePassword(Integer userId, String newPassword);
}
