package com.questify.controller;

import com.questify.config.security.JwtUtil;
import com.questify.domain.Role;
import com.questify.domain.User;
import com.questify.dto.AuthDtos;
import com.questify.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RestController
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtUtil jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record LoginReq(String usernameOrEmail, String password) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthRes register(@RequestBody @Validated AuthDtos.RegisterReq req) {
        if (users.existsByUsername(req.username()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken.");
        if (users.existsByEmail(req.email()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use.");

        var u = new User();
        u.setUsername(req.username().trim());
        u.setEmail(req.email().toLowerCase().trim());
        u.setDisplayName(req.displayName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRoles(Set.of(Role.USER));

        var saved = users.save(u);

        String token = jwt.generateToken(saved.getUsername());

        var userOut = new AuthDtos.UserOut(
                saved.getId(), saved.getUsername(), saved.getEmail(), saved.getDisplayName(),
                saved.getRoles(), saved.getCreatedAt(), saved.getUpdatedAt()
        );

        return new AuthDtos.AuthRes(userOut, token, null);
    }

    @PostMapping("/login")
    public AuthDtos.AuthRes login(@RequestBody LoginReq req) {
        var user = users.findByUsername(req.usernameOrEmail())
                .or(() -> users.findByEmail(req.usernameOrEmail()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        var userOut = new AuthDtos.UserOut(
                user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                user.getRoles(), user.getCreatedAt(), user.getUpdatedAt()
        );

        String token = jwt.generateToken(user.getUsername());

        return new AuthDtos.AuthRes(userOut, token, null);
    }
}
