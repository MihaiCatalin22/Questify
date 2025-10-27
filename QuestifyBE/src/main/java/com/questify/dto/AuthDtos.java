package com.questify.dto;

import com.questify.domain.Role;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Set;

public class AuthDtos {

    public record RegisterReq(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 128)
            @Pattern(
                    regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-\\=\\[\\]{};':\"\\\\|,.<>/?]).{8,128}$",
                    message = "Password must be 8–128 chars and include an uppercase letter, a number, and a special character"
            )
            String password,
            @NotBlank @Size(max = 64) String displayName
    ) {}
    public record UserOut(Long id, String username, String email, String displayName,
                          Set<Role> roles, Instant createdAt, Instant updatedAt) {}

    public record AuthRes(
            UserOut user,
            String jwt,
            String expiresAt
    ) {}
}
