package com.einar115.thearchivist.config;

import com.einar115.thearchivist.dto.request.UserRequest;
import com.einar115.thearchivist.dto.response.UserResponse;
import com.einar115.thearchivist.entity.RoleEntity;
import com.einar115.thearchivist.repository.UserRepository;
import com.einar115.thearchivist.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the initial administrator on a fresh install. Without it there is no way to sign in:
 * /register itself requires ADMIN_DOCUMENTS, so the first account cannot be created through the API.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final String ADMIN_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final UserService userService;
    private final String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                UserService userService,
                                @Value("${app.bootstrap.admin-password}") String adminPassword) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Only on an empty table: an administrator deleted on purpose must not come back on restart.
        if (userRepository.count() > 0) {
            return;
        }

        // createUser already hashes the password with the PasswordEncoder bean: do not encode it here.
        UserResponse admin = userService.createUser(new UserRequest(
                ADMIN_USERNAME,
                adminPassword,
                RoleEntity.RoleEnum.ADMIN_DOCUMENTS));

        if (DEFAULT_PASSWORD.equals(adminPassword)) {
            LOGGER.warn("Bootstrap administrator '{}' created with the default password. "
                    + "Set ADMIN_PASSWORD before exposing this instance.", admin.username());
        } else {
            LOGGER.info("Bootstrap administrator '{}' created.", admin.username());
        }
    }
}
