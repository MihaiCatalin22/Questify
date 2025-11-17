package com.questify.service;

import com.questify.domain.UserProfile;
import com.questify.dto.ProfileDtos.UpsertMeReq;
import com.questify.repository.UserProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserProfileService {
    private final UserProfileRepository repo;

    public UserProfileService(UserProfileRepository repo) { this.repo = repo; }

    @Transactional
    public UserProfile ensure(String userId, String username, String displayName, String email, String avatar) {
        return repo.findById(userId).orElseGet(() -> {
            var p = UserProfile.builder()
                    .userId(userId)
                    .username(username)
                    .displayName(displayName != null ? displayName : username)
                    .email(email)
                    .avatarUrl(avatar)
                    .bio(null)
                    .build();
            return repo.save(p);
        });
    }

    @Transactional
    public UserProfile upsertMe(String userId, UpsertMeReq req) {
        var p = repo.findById(userId).orElseThrow(() -> new EntityNotFoundException("Profile not found"));
        if (req.username() != null) p.setUsername(req.username());
        if (req.displayName() != null) p.setDisplayName(req.displayName());
        if (req.email() != null) p.setEmail(req.email());
        if (req.avatarUrl() != null) p.setAvatarUrl(req.avatarUrl());
        if (req.bio() != null) p.setBio(req.bio());
        return repo.save(p);
    }

    public UserProfile get(String id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("User %s not found".formatted(id)));
    }

    public List<UserProfile> search(String q) {
        return repo.findTop20ByUsernameStartingWithIgnoreCaseOrDisplayNameStartingWithIgnoreCase(q, q);
    }

    public List<UserProfile> bulk(List<String> ids) {
        return repo.findAllById(ids);
    }
}
