package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.dto.request.AuthRequest;
import com.einar115.thearchivist.dto.request.UserRequest;
import com.einar115.thearchivist.dto.response.AuthResponse;
import com.einar115.thearchivist.dto.response.UserResponse;
import com.einar115.thearchivist.model.MainUser;
import com.einar115.thearchivist.service.AuthService;
import com.einar115.thearchivist.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    // Not a context bean: it is taken from the holder, the same way Spring Security filters do.
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final UserService userService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           SessionAuthenticationStrategy sessionAuthenticationStrategy,
                           SecurityContextRepository securityContextRepository,
                           UserService userService) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.userService = userService;
    }

    @Override
    public AuthResponse login(AuthRequest authRequest, HttpServletRequest req, HttpServletResponse res) {
        // Throws AuthenticationException on bad credentials; the @RestControllerAdvice maps it.
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        authRequest.username(), authRequest.password()));

        // Rotates the session id (fixation) and issues a fresh CSRF token.
        sessionAuthenticationStrategy.onAuthentication(authentication, req, res);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        // SecurityContextHolderFilter only loads the context, it never saves it: persist it here.
        securityContextRepository.saveContext(context, req, res);

        MainUser principal = (MainUser) authentication.getPrincipal();
        return new AuthResponse(principal.getUsername(), principal.getRoles());
    }

    @Override
    public UserResponse register(UserRequest userRequest) {
        return userService.createUser(userRequest);
    }
}
