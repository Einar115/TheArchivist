package com.einar115.thearchivist.service;

import com.einar115.thearchivist.dto.request.AuthRequest;
import com.einar115.thearchivist.dto.request.RefreshRequest;
import com.einar115.thearchivist.dto.response.AuthResponse;
import com.einar115.thearchivist.dto.response.LogoutResponse;
import com.einar115.thearchivist.dto.response.RefreshResponse;

public interface AuthService {
    AuthResponse login(AuthRequest authRequest);
    RefreshResponse refreshToken(RefreshRequest refreshRequest);
    LogoutResponse logout(RefreshRequest refreshRequest);
}
