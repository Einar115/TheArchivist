package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.dto.request.AuthRequest;
import com.einar115.thearchivist.dto.request.UserRequest;
import com.einar115.thearchivist.dto.response.AuthResponse;
import com.einar115.thearchivist.dto.response.UserResponse;
import com.einar115.thearchivist.entity.RoleEntity;
import com.einar115.thearchivist.entity.UserEntity;
import com.einar115.thearchivist.model.MainUser;
import com.einar115.thearchivist.repository.UserRepository;
import com.einar115.thearchivist.service.AuthService;
import com.einar115.thearchivist.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final UserService userService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           SessionAuthenticationStrategy sessionAuthenticationStrategy,
                           SecurityContextHolderStrategy securityContextHolderStrategy,
                           SecurityContextRepository securityContextRepository,
                           UserService userService){
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextHolderStrategy = securityContextHolderStrategy;
        this.securityContextRepository = securityContextRepository;
        this.userService = userService;
    }

    @Override
    public AuthResponse login(AuthRequest authRequest, HttpServletRequest req, HttpServletResponse res){
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(authRequest.username(), authRequest.password()));

        sessionAuthenticationStrategy.onAuthentication(authentication, req, res);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, req, res);

        MainUser principal = (MainUser) authentication.getPrincipal();
        assert principal != null;
        return new AuthResponse(principal.getUsername(), principal.getRoles());
    }

    @Override
    public UserResponse register(UserRequest userRequest) {
        return null;
    }

}
