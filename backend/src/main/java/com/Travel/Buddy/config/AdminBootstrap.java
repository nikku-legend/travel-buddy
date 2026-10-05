package com.Travel.Buddy.config;

import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.repository.UserRepository;
import com.Travel.Buddy.service.partner.RoleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first super administrator. (fixes a deployment dead end)
 *
 * <p>Without this, a fresh install has no {@code ROLE_SUPER_ADMIN} user
 * and nothing in the application can create one, because the only route
 * to that role is partner approval, which itself needs admin access.
 * The admin console is therefore unreachable until someone edits the
 * database by hand.
 *
 * <p>This is <strong>opt-in and off by default</strong>. It only runs
 * when {@code app.bootstrap.admin-email} and
 * {@code app.bootstrap.admin-password} are both supplied, so a
 * production deployment can never silently create a default admin.
 *
 * <p>It is idempotent: an existing account is never overwritten, so
 * restarting the application cannot reset an admin's password.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;

    private final RoleService roleService;

    private final PasswordEncoder passwordEncoder;

    private final String adminEmail;

    private final String adminPassword;

    private final String adminName;

    public AdminBootstrap(
            UserRepository userRepository,
            RoleService roleService,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-email:}")
            String adminEmail,
            @Value("${app.bootstrap.admin-password:}")
            String adminPassword,
            @Value("${app.bootstrap.admin-name:Travel Buddy Admin}")
            String adminName
    ) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        if (adminEmail == null
                || adminEmail.isBlank()
                || adminPassword == null
                || adminPassword.isBlank()) {

            return;
        }

        String email =
                adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {

            log.info(
                    "Admin bootstrap skipped: {} already exists",
                    email
            );

            return;
        }

        User admin = new User();

        admin.setFullName(adminName);
        admin.setEmail(email);
        admin.setPasswordHash(
                passwordEncoder.encode(adminPassword)
        );
        admin.setRole(Role.ROLE_SUPER_ADMIN);

        User saved = userRepository.save(admin);

        roleService.grantBaselineTravelerRole(saved);
        roleService.grant(
                saved,
                Role.ROLE_SUPER_ADMIN,
                null
        );

        log.warn(
                "Bootstrapped super admin '{}'. "
                        + "Set app.bootstrap.admin-password to an empty "
                        + "value once you have signed in.",
                email
        );
    }
}
