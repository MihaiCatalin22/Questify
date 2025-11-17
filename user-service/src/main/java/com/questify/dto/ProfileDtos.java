package com.questify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ProfileDtos {

    public record UpsertMeReq(
            @Size(max=64) String username,
            @Size(max=128) String displayName,
            @Email @Size(max=256) String email,
            @Size(max=512) String avatarUrl,
            @Size(max=512) String bio
    ) {}

    public record ProfileRes(
            String userId,
            String username,
            String displayName,
            String email,
            String avatarUrl,
            String bio
    ) {}

    public record BulkRequest(List<String> ids) {}
    public record BulkResponse(List<ProfileRes> profiles) {}
}
