package com.traceguard.auth;

import com.traceguard.audit.AuditService;
import com.traceguard.common.Errors.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Sign-in and user creation. */
@Service
public class UserService {

    public record Session(String token, Role role) {}

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public UserService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }

    /** Same error for unknown email, wrong password and disabled account, so accounts cannot be probed. */
    public Session login(String email, String password) {
        AppUser user = users.findByEmail(email)
                .filter(AppUser::isEnabled)
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        audit.recordAs(user.getEmail(), "LOGIN", user.getEmail());
        return new Session(jwt.issue(user), user.getRole());
    }

    public AppUser register(String email, String password, Role role, Long organizationId) {
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        AppUser saved = users.save(new AppUser(email, encoder.encode(password), role, organizationId));
        audit.record("USER_CREATED", saved.getEmail());
        return saved;
    }
}
