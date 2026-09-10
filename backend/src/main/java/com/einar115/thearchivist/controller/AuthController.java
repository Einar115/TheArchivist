package com.einar115.thearchivist.controller;

import com.einar115.thearchivist.dto.request.AuthRequest;
import com.einar115.thearchivist.dto.request.UserRequest;
import com.einar115.thearchivist.dto.response.AuthResponse;
import com.einar115.thearchivist.dto.response.UserResponse;
import com.einar115.thearchivist.model.MainUser;
import com.einar115.thearchivist.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logout is not declared here: it is handled by the LogoutFilter configured in SecurityConfig,
 * which invalidates the session, deletes the cookie and refreshes the CSRF token.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest authRequest, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(authRequest, request, response));
    }

    // Returns 204 when nobody is logged in: this is the bootstrap call of the SPA.
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal MainUser user) {
        if (user == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new AuthResponse(user.getUsername(), user.getRoles()));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest userRequest) {
        UserResponse created = authService.register(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
