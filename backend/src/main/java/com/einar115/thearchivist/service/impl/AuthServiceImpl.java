package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.dto.request.AuthRequest;
import com.einar115.thearchivist.dto.request.RefreshRequest;
import com.einar115.thearchivist.dto.response.AuthResponse;
import com.einar115.thearchivist.dto.response.LogoutResponse;
import com.einar115.thearchivist.dto.response.RefreshResponse;
import com.einar115.thearchivist.entity.RefreshTokenEntity;
import com.einar115.thearchivist.entity.UserEntity;
import com.einar115.thearchivist.repository.RefreshTokenRepository;
import com.einar115.thearchivist.repository.UserRepository;
import com.einar115.thearchivist.security.JwtProvider;
import com.einar115.thearchivist.service.AuthService;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;


    public AuthServiceImpl(JwtProvider jwtProvider,
                           UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           AuthenticationManager authenticationManager) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public AuthResponse login(AuthRequest authRequest) {
        try {
            // Authenticate with spring security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            authRequest.username(),
                            authRequest.password()
                    )
            );

            // Extract authenticated user
            UserEntity user = userRepository.findByUsername(authRequest.username())
                    .orElseThrow(() -> new RuntimeException("user not found"));

            // Generate tokens
            String accessToken = jwtProvider.generateAccessToken(user.getId());
            String refreshToken = jwtProvider.generateRefreshToken(user.getId(), user.getUsername(), "web"); // 'deviceId' no defined yet, generic 'web' for now

            // Save refreshToken in DB
            UUID jti = jwtProvider.extractJti(refreshToken);
            RefreshTokenEntity tokenEntity = new RefreshTokenEntity();
            tokenEntity.setUser(user);
            tokenEntity.setJti(jti);
            tokenEntity.setDeviceId("web");
            tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(7));
            tokenEntity.setActive(true);
            refreshTokenRepository.save(tokenEntity);

            // Extract roles
            List<String> roles = user.getRoles().stream()
                    .map(role -> role.getName().name())
                    .toList();

            return new AuthResponse(
                    user.getUsername(),
                    roles,
                    accessToken,
                    refreshToken
            );

        } catch (AuthenticationException e) {
            throw new RuntimeException("Incorrect credentials: " + e.getMessage());
        }
    }

    @Override
    public RefreshResponse refreshToken(RefreshRequest refreshRequest) {
        try {
            Integer userId = jwtProvider.extractUserId(refreshRequest.refreshToken());
            UUID jti = jwtProvider.extractJti(refreshRequest.refreshToken());

            RefreshTokenEntity refreshToken = refreshTokenRepository.findByJti(jti)
                    .orElseThrow(() -> new RuntimeException("Invalid token"));

            if (!refreshToken.isActive()) {
                throw new RuntimeException("Token has been revoked");
            }

            if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                refreshToken.setActive(false);
                refreshTokenRepository.save(refreshToken);
                throw new RuntimeException("Token expired");
            }

            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String newAccessToken = jwtProvider.generateAccessToken(user.getId());
            return new RefreshResponse(newAccessToken);

        } catch (JwtException e){
            throw new RuntimeException("Invalid refresh token" + e.getMessage());
        }
    }

    @Override
    public LogoutResponse logout(RefreshRequest refreshRequest) {
        try {
            UUID jti = jwtProvider.extractJti(refreshRequest.refreshToken());
            RefreshTokenEntity refreshToken = refreshTokenRepository.findByJti(jti)
                    .orElseThrow(() -> new RuntimeException("Invalid token"));
            refreshToken.setActive(false);
            refreshTokenRepository.save(refreshToken);
            return new LogoutResponse("Session closed successfully");
        } catch (JwtException e) {
            throw new RuntimeException("Invalid refresh token" + e.getMessage());
        }
    }

}
