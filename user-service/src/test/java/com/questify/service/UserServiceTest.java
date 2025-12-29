package com.questify.service;

import com.questify.domain.UserProfile;
import com.questify.dto.ProfileDtos;
import com.questify.dto.ProfileDtos.UpsertMeReq;
import com.questify.kafka.EventPublisher;
import com.questify.repository.UserProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserProfileRepository repo;
    @Mock EventPublisher events;
    @Mock KeycloakAdminService keycloakAdmin;

    @InjectMocks UserProfileService service;

    private UserProfile existing;

    @BeforeEach
    void setUp() {
        service.init();

        existing = UserProfile.builder()
                .userId("u1")
                .username("alice")
                .displayName("Alice")
                .email("a@x.com")
                .avatarUrl("http://a.png")
                .bio("hi")
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-02T00:00:00Z"))
                .build();
    }

    /* =========================================================================================
     * HAPPY PATHS
     * ========================================================================================= */

    @Test
    void ensure_creates_when_absent_and_publishes_UserRegistered() {
        when(repo.findById("u2")).thenReturn(Optional.empty());
        when(repo.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        var saved = service.ensure("u2", "bob", "Bobby", "b@x.com", "http://b.png");

        assertThat(saved.getUserId()).isEqualTo("u2");
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getDisplayName()).isEqualTo("Bobby");
        assertThat(saved.getEmail()).isEqualTo("b@x.com");
        assertThat(saved.getAvatarUrl()).isEqualTo("http://b.png");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCap = ArgumentCaptor.forClass(Map.class);

        verify(events).publish(
                eq("users"),
                eq("u2"),
                eq("UserRegistered"), eq(1), eq("user-service"),
                payloadCap.capture()
        );

        var payload = payloadCap.getValue();
        assertThat(payload.get("userId")).isEqualTo("u2");
        assertThat(payload.get("username")).isEqualTo("bob");
        assertThat(payload.get("displayName")).isEqualTo("Bobby");
        assertThat(payload.get("email")).isEqualTo("b@x.com");
        assertThat(payload.get("avatarUrl")).isEqualTo("http://b.png");
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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCap = ArgumentCaptor.forClass(Map.class);

        verify(events).publish(
                eq("users"),
                eq("u1"),
                eq("UserProfileUpdated"), eq(1), eq("user-service"),
                payloadCap.capture()
        );

        var payload = payloadCap.getValue();
        assertThat(payload.get("userId")).isEqualTo("u1");
        assertThat(payload.get("username")).isEqualTo("alice");
        assertThat(payload.get("displayName")).isEqualTo("Alice Cooper");
        assertThat(payload.get("email")).isEqualTo("a@x.com");
        assertThat(payload.get("avatarUrl")).isEqualTo("http://new.png");
        assertThat(payload.get("bio")).isEqualTo("updated bio");
    }

    @Test
    void exportMe_returns_dto() {
        when(repo.findById("u1")).thenReturn(Optional.of(existing));

        var res = service.exportMe("u1");

        assertThat(res.userId()).isEqualTo("u1");
        assertThat(res.username()).isEqualTo("alice");
        assertThat(res.displayName()).isEqualTo("Alice");
        assertThat(res.email()).isEqualTo("a@x.com");
        assertThat(res.avatarUrl()).isEqualTo("http://a.png");
        assertThat(res.bio()).isEqualTo("hi");
        assertThat(res.createdAt()).isEqualTo(existing.getCreatedAt());
        assertThat(res.updatedAt()).isEqualTo(existing.getUpdatedAt());
    }

    @Test
    void deleteMe_soft_deletes_profile_publishes_UserDeleted_and_attempts_idp_disable() {
        var p = existing.toBuilder()
                .userId("abcdef123456")
                .username("alice")
                .displayName("Alice")
                .email("a@x.com")
                .avatarUrl("http://a.png")
                .bio("hi")
                .deletionRequestedAt(null)
                .deletedAt(null)
                .build();

        when(repo.findById("abcdef123456")).thenReturn(Optional.of(p));
        when(repo.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.deleteMe("abcdef123456");

        assertThat(res.userId()).isEqualTo("abcdef123456");
        assertThat(res.deletionRequestedAt()).isNotNull();
        assertThat(res.deletedAt()).isNotNull();

        ArgumentCaptor<UserProfile> savedCap = ArgumentCaptor.forClass(UserProfile.class);
        verify(repo).save(savedCap.capture());
        var saved = savedCap.getValue();

        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getDisplayName()).isEqualTo("Deleted user");
        assertThat(saved.getAvatarUrl()).isNull();
        assertThat(saved.getBio()).isNull();
        assertThat(saved.getUsername()).isEqualTo("deleted-abcdef12"); // shortId first 8 chars
        assertThat(saved.getDeletedAt()).isNotNull();
        assertThat(saved.getDeletionRequestedAt()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(
                eq("users"),
                eq("abcdef123456"),
                eq("UserDeleted"), eq(1), eq("user-service"),
                payloadCap.capture()
        );
        assertThat(payloadCap.getValue().get("userId")).isEqualTo("abcdef123456");
        assertThat(payloadCap.getValue().get("deletedAt")).isNotNull();

        verify(keycloakAdmin).disableAndLogout("abcdef123456");
    }

    @Test
    void retryDisableIdpForDeletedProfiles_calls_idp_for_each_and_continues_on_errors() {
        var p1 = UserProfile.builder().userId("u1").deletedAt(Instant.now()).build();
        var p2 = UserProfile.builder().userId("u2").deletedAt(Instant.now()).build();
        when(repo.findTop200ByDeletedAtIsNotNullOrderByDeletedAtDesc()).thenReturn(List.of(p1, p2));

        doThrow(new RuntimeException("boom")).when(keycloakAdmin).disableAndLogout("u1");

        service.retryDisableIdpForDeletedProfiles();

        verify(keycloakAdmin).disableAndLogout("u1");
        verify(keycloakAdmin).disableAndLogout("u2");
    }

    /* =========================================================================================
     * UNHAPPY PATHS
     * ========================================================================================= */

    @Test
    void upsertMe_throws_when_not_found() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertMe("missing", new UpsertMeReq(null, null, null, null, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Profile not found");
    }

    @Test
    void upsertMe_throws_when_profile_is_deleted() {
        var deleted = existing.toBuilder()
                .deletedAt(Instant.parse("2025-01-10T00:00:00Z"))
                .build();
        when(repo.findById("u1")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.upsertMe("u1", new UpsertMeReq("x", null, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Profile has been deleted");

        verify(repo, never()).save(any());
        verify(events, never()).publish(any(), any(), any(), anyInt(), any(), anyMap());
    }

    @Test
    void exportMe_throws_when_not_found() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportMe("missing"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Profile not found");
    }

    @Test
    void deleteMe_when_already_deleted_returns_existing_and_does_not_publish_or_save() {
        var alreadyDeleted = existing.toBuilder()
                .deletionRequestedAt(Instant.parse("2025-01-03T00:00:00Z"))
                .deletedAt(Instant.parse("2025-01-04T00:00:00Z"))
                .build();
        when(repo.findById("u1")).thenReturn(Optional.of(alreadyDeleted));

        var res = service.deleteMe("u1");

        assertThat(res.userId()).isEqualTo("u1");
        assertThat(res.deletionRequestedAt()).isEqualTo(alreadyDeleted.getDeletionRequestedAt());
        assertThat(res.deletedAt()).isEqualTo(alreadyDeleted.getDeletedAt());

        verify(repo, never()).save(any());
        verify(events, never()).publish(any(), any(), any(), anyInt(), any(), anyMap());
        verify(keycloakAdmin).disableAndLogout("u1");
    }

    @Test
    void deleteMe_still_returns_success_even_if_idp_disable_throws() {
        when(repo.findById("u1")).thenReturn(Optional.of(existing));
        when(repo.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("idp down")).when(keycloakAdmin).disableAndLogout("u1");

        var res = service.deleteMe("u1");

        assertThat(res.userId()).isEqualTo("u1");
        assertThat(res.deletedAt()).isNotNull();

        verify(events).publish(eq("users"), eq("u1"), eq("UserDeleted"), eq(1), eq("user-service"), anyMap());
        verify(keycloakAdmin).disableAndLogout("u1");
    }

    /* =========================================================================================
     * EDGE CASES
     * ========================================================================================= */

    @Test
    void ensure_trims_and_uses_username_when_displayName_blank() {
        when(repo.findById("u9")).thenReturn(Optional.empty());
        when(repo.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        var saved = service.ensure("u9", "  bob  ", "   ", "  b@x.com ", "  ");

        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getDisplayName()).isEqualTo("bob");  // falls back to username
        assertThat(saved.getEmail()).isEqualTo("b@x.com");
        assertThat(saved.getAvatarUrl()).isNull();            // blank -> null
    }

    @Test
    void hardDeleteOldDeletedProfiles_does_nothing_when_days_is_zero_or_negative() {
        ReflectionTestUtils.setField(service, "hardDeleteDays", 0);

        service.hardDeleteOldDeletedProfiles();

        verify(repo, never()).deleteByDeletedAtBefore(any());
    }

    @Test
    void hardDeleteOldDeletedProfiles_deletes_before_cutoff_when_days_positive() {
        ReflectionTestUtils.setField(service, "hardDeleteDays", 30);

        var beforeCall = Instant.now().minus(Duration.ofDays(30)).minusSeconds(5);
        service.hardDeleteOldDeletedProfiles();

        ArgumentCaptor<Instant> cap = ArgumentCaptor.forClass(Instant.class);
        verify(repo).deleteByDeletedAtBefore(cap.capture());

        var cutoff = cap.getValue();
        assertThat(cutoff).isAfter(beforeCall);
        assertThat(cutoff).isBefore(Instant.now().minus(Duration.ofDays(29)));
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
        when(repo.findAllById(List.of("u1", "u2"))).thenReturn(List.of(existing, p2));

        assertThat(service.search("al")).containsExactly(existing, p2);
        assertThat(service.bulk(List.of("u1", "u2"))).containsExactly(existing, p2);
    }
}
