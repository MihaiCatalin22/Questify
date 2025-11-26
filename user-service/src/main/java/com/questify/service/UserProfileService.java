package com.questify.service;

import com.questify.domain.UserProfile;
import com.questify.dto.ProfileDtos.UpsertMeReq;
import com.questify.kafka.EventPublisher;
import com.questify.repository.UserProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserProfileService {
    private final UserProfileRepository repo;
    private final EventPublisher events;

    @Value("${app.kafka.topics.users:users}")
    private String usersTopic;

    public UserProfileService(UserProfileRepository repo, EventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

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
            var saved = repo.save(p);

            events.publish(
                    usersTopic,
                    saved.getUserId(),
                    "UserRegistered", 1, "user-service",
                    Map.of(
                            "userId", saved.getUserId(),
                            "username", saved.getUsername(),
                            "displayName", saved.getDisplayName(),
                            "email", saved.getEmail(),
                            "avatarUrl", saved.getAvatarUrl()
                    )
            );

            return saved;
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
        var saved = repo.save(p);

        events.publish(
                usersTopic,
                saved.getUserId(),
                "UserProfileUpdated", 1, "user-service",
                Map.of(
                        "userId", saved.getUserId(),
                        "username", saved.getUsername(),
                        "displayName", saved.getDisplayName(),
                        "email", saved.getEmail(),
                        "avatarUrl", saved.getAvatarUrl(),
                        "bio", saved.getBio()
                )
        );

        return saved;
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
