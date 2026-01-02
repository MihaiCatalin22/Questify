package com.questify.gdpr.service;

import com.questify.domain.UserProfile;
import com.questify.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserProfileExportAssembler {

    private final UserProfileRepository profiles;

    public Map<String, Object> exportForUser(String userId) {
        UserProfile p = profiles.findById(userId).orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);

        if (p == null) {
            out.put("profile", null);
            return out;
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", p.getUserId());
        profile.put("username", p.getUsername());
        profile.put("displayName", p.getDisplayName());
        profile.put("email", p.getEmail());
        profile.put("avatarUrl", p.getAvatarUrl());
        profile.put("bio", p.getBio());
        profile.put("createdAt", p.getCreatedAt());
        profile.put("updatedAt", p.getUpdatedAt());
        profile.put("deletionRequestedAt", p.getDeletionRequestedAt());
        profile.put("deletedAt", p.getDeletedAt());

        out.put("profile", profile);
        return out;
    }
}
