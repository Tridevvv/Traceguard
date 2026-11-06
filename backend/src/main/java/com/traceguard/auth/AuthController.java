package com.traceguard.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record LoginResponse(String token, Role role) {}
    public record RegisterRequest(@Email @NotBlank String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotNull Role role,
                                  Long organizationId) {}
    public record UserResponse(Long id, String email, Role role, Long organizationId) {}

    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        UserService.Session s = users.login(req.email(), req.password());
        return new LoginResponse(s.token(), s.role());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse register(@Valid @RequestBody RegisterRequest req) {
        AppUser u = users.register(req.email(), req.password(), req.role(), req.organizationId());
        return new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.getOrganizationId());
    }
}
