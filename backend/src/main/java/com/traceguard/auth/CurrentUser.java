package com.traceguard.auth;

import com.traceguard.common.Errors.NotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the signed-in user from the JWT principal (the email). */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser get() {
        String email = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
