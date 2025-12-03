package com.questify.service;

import com.questify.domain.UserProfile;
import com.questify.dto.ProfileDtos.UpsertMeReq;
import com.questify.kafka.EventPublisher;
import com.questify.repository.UserProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock UserProfileRepository repo;
    @Mock EventPublisher events;

    @InjectMocks UserProfileService service;

    private UserProfile existing;

    @BeforeEach
    void setUp() {
        existing = UserProfile.builder()
                .userId("u1")
                .username("alice")
                .displayName("Alice")
                .email("a@x.com")
                .avatarUrl("http://a.png")
                .bio("hi")
                .build();
    }

    @Test
    void ensure_creates_when_absent_and_publishes_UserRegistered() {
        when(repo.findById("u2")).thenReturn(Optional.empty());

        var toSave = ArgumentCaptor.forClass(UserProfile.class);
        when(repo.save(toSave.capture())).thenAnswer(inv -> {
            var p = toSave.getValue();
            return p; // simulate persisted entity w/ same data
        });

        var saved = service.ensure("u2", "bob", "Bobby", "b@x.com", "http://b.png");

        assertThat(saved.getUserId()).isEqualTo("u2");
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getDisplayName()).isEqualTo("Bobby");
        assertThat(saved.getEmail()).isEqualTo("b@x.com");
        assertThat(saved.getAvatarUrl()).isEqualTo("http://b.png");

        verify(events).publish(
                eq("users"),
                eq("u2"),
                eq("UserRegistered"), eq(1), eq("user-service"),
                argThat((Map<String,Object> m) ->
                        "u2".equals(m.get("userId")) &&
                                "bob".equals(m.get("username")) &&
                                "Bobby".equals(m.get("displayName")) &&
                                "b@x.com".equals(m.get("email")) &&
                                "http://b.png".equals(m.get("avatarUrl"))
                )
        );
    }

    @Test
    void ensure_returns_existing_when_present_and_does_not_publish() {
        when(repo.findById("u1")).thenReturn(Optional.of(existing));

        var res = service.ensure("u1", "ignored", "ignored", "ignored", "ignored");

        assertThat(res).isSameAs(existing);
        verify(repo, never()).save(any());
        verify(events, never()).publish(any(), any(), any(), anyInt(), any(), anyMap());
    }

    @Test
    void upsertMe_updates_only_non_null_fields_and_publishes_UserProfileUpdated() {
        when(repo.findById("u1")).thenReturn(Optional.of(existing));
        when(repo.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpsertMeReq(
                null,                    // username unchanged
                "Alice Cooper",          // displayName
                null,                    // email unchanged
                "http://new.png",        // avatarUrl
                "updated bio"            // bio
        );

        var saved = service.upsertMe("u1", req);

        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getDisplayName()).isEqualTo("Alice Cooper");
        assertThat(saved.getEmail()).isEqualTo("a@x.com");
        assertThat(saved.getAvatarUrl()).isEqualTo("http://new.png");
        assertThat(saved.getBio()).isEqualTo("updated bio");

        verify(events).publish(
                eq("users"),
                eq("u1"),
                eq("UserProfileUpdated"), eq(1), eq("user-service"),
                argThat((Map<String,Object> m) ->
                        "u1".equals(m.get("userId")) &&
                                "alice".equals(m.get("username")) &&
                                "Alice Cooper".equals(m.get("displayName")) &&
                                "a@x.com".equals(m.get("email")) &&
                                "http://new.png".equals(m.get("avatarUrl")) &&
                                "updated bio".equals(m.get("bio"))
                )
        );
    }

    @Test
    void upsertMe_throws_when_not_found() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.upsertMe("missing", new UpsertMeReq(null,null,null,null,null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Profile not found");
    }

    @Test
    void get_returns_profile_or_throws() {
        when(repo.findById("u1")).thenReturn(Optional.of(existing));
        assertThat(service.get("u1")).isSameAs(existing);

        when(repo.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User nope not found");
    }

    @Test
    void search_and_bulk_delegate_to_repo() {
        var p2 = existing.toBuilder().userId("u2").username("alina").displayName("Alina").build();

        when(repo.findTop20ByUsernameStartingWithIgnoreCaseOrDisplayNameStartingWithIgnoreCase("al", "al"))
                .thenReturn(List.of(existing, p2));
        when(repo.findAllById(List.of("u1","u2"))).thenReturn(List.of(existing, p2));

        assertThat(service.search("al")).containsExactly(existing, p2);
        assertThat(service.bulk(List.of("u1","u2"))).containsExactly(existing, p2);
    }
}

