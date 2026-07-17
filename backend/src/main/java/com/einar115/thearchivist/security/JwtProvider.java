package com.einar115.thearchivist.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {
    private final SecretKey key;
    private final Long accessTokenExpirationMs;
    private final Long refreshTokenExpirationMs;

    public JwtProvider(@Value("${app.jwt.secret}") String jwtSecret,
                       @Value("${app.jwt.access-token-expiration:900000}") Long accessTokenExpirationMs,
                       @Value("${app.jwt.refresh-token-expiration}") Long refreshTokenExpirationMs) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(Integer userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(Integer userId, String username, String deviceId) throws JwtException {
        UUID jti = UUID.randomUUID();

        return Jwts.builder()
                .subject(userId.toString())
                .id(jti.toString())
                .claim("username", username)
                .claim("deviceId", deviceId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Integer extractUserId(String token) throws JwtException {
        String userId = validateToken(token).getSubject();
        return Integer.parseInt(userId);
    }

    public String extractUsername(String token) throws JwtException {
        return validateToken(token).get("username", String.class);
    }

    public UUID extractJti(String token) throws JwtException {
        String jti = validateToken(token).getId();
        return UUID.fromString(jti);
    }

    public String extractDeviceId(String token) throws JwtException {
        return (String) validateToken(token).get("deviceId");
    }

    public boolean isTokenExpired(String token) {
        try {
            validateToken(token);
            return false;
        }  catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

}

