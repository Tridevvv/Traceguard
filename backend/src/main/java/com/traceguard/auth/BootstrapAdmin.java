package com.traceguard.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first ADMIN from configuration so the system is usable on a fresh database. */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public BootstrapAdmin(AppUserRepository users, PasswordEncoder encoder,
                          @Value("${traceguard.bootstrap.admin-email:}") String email,
                          @Value("${traceguard.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!email.isBlank() && !password.isBlank() && !users.existsByEmail(email)) {
            users.save(new AppUser(email, encoder.encode(password), Role.ADMIN, null));
        }
    }
}
