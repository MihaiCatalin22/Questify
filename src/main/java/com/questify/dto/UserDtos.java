package com.questify.dto;

import jakarta.validation.constraints.*;

public class UserDtos {
    public record CreateUserReq(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 60, max = 120) String passwordHash,
            @Size(max = 128) String displayName
           // @Size(max = 512) String avatarUrl
    ) {}

    public record UserRes(
            Long id,
            String username,
            String email,
            String displayName
            // String avatarUrl
    ) {}
}
