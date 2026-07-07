package com.einar115.thearchivist.service;

import com.einar115.thearchivist.dto.*;

public interface AuthService {
    AuthResponse login(AuthRequest authRequest);
    RefreshResponse refreshToken(RefreshRequest refreshRequest);
    LogoutResponse logout(RefreshRequest refreshRequest);
}
