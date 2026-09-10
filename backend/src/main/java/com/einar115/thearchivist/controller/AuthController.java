package com.einar115.thearchivist.controller;

import com.einar115.thearchivist.dto.request.AuthRequest;
import com.einar115.thearchivist.dto.request.UserRequest;
import com.einar115.thearchivist.dto.response.AuthResponse;
import com.einar115.thearchivist.dto.response.UserResponse;
import com.einar115.thearchivist.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest authRequest){
        return ResponseEntity.ok(authService.login(authRequest));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(){
        return null;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody UserRequest userRequest){
        return null;
    }

}
