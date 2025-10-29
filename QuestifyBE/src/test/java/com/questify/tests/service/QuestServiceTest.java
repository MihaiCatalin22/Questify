package com.questify.tests.service;

import com.questify.config.NotFoundException;
import com.questify.config.security.CustomUserDetails;
import com.questify.domain.*;
import com.questify.dto.QuestDtos;
import com.questify.mapper.QuestMapper;
import com.questify.persistence.QuestCompletionRepository;
import com.questify.persistence.QuestRepository;
import com.questify.persistence.UserRepository;
import com.questify.service.QuestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class QuestServiceTest {

    @Mock QuestRepository quests;
    @Mock UserRepository users;
    @Mock QuestMapper mapper;
    @Mock QuestCompletionRepository questCompletions;

    @InjectMocks QuestService service;

    private final AtomicLong ids = new AtomicLong(1);

    /* ----------------------- defaults & helpers ----------------------- */

    @BeforeEach
    void defaults() {
        when(quests.save(any(Quest.class))).thenAnswer(inv -> {
            Quest q = inv.getArgument(0);
            if (q.getId() == null) q.setId(ids.getAndIncrement());
            return q;
        });

        when(mapper.toRes(any(Quest.class))).thenAnswer(inv -> {
            Quest q = inv.getArgument(0);
            if (q == null) return null;
            return new QuestDtos.QuestRes(
                    q.getId(),
                    q.getTitle(),
                    q.getDescription(),
                    q.getCategory(),
                    q.getStatus(),
                    q.getStartDate(),
                    q.getEndDate(),
                    q.getCreatedAt(),
                    q.getUpdatedAt(),
                    q.getCreatedBy() != null ? q.getCreatedBy().getId() : null,
                    q.getParticipants() != null ? q.getParticipants().size() : 0,
                    false,
                    q.getVisibility()
            );
        });

        when(questCompletions.existsByQuest_IdAndUser_IdAndStatus(anyLong(), anyLong(), any()))
                .thenReturn(false);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuth(Long userId, String... roles) {
        CustomUserDetails cud = mock(CustomUserDetails.class);
        when(cud.getId()).thenReturn(userId);

        Collection<GrantedAuthority> authorities = new HashSet<>();
        for (String r : roles) {
            String name = r.startsWith("ROLE_") ? r : "ROLE_" + r;
            authorities.add(new SimpleGrantedAuthority(name));
        }

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(cud);
        doReturn(authorities).when(authentication).getAuthorities();

        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(sc);
    }

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        return u;
    }

    private Quest quest(long id, Long ownerId, QuestStatus status, QuestVisibility vis) {
        Quest q = new Quest();
        q.setId(id);
        q.setTitle("Q" + id);
        q.setDescription("D" + id);
        q.setCategory(QuestCategory.OTHER);
        q.setStatus(status);
        q.setVisibility(vis);
        q.setStartDate(Instant.parse("2025-01-01T00:00:00Z"));
        q.setEndDate(Instant.parse("2025-12-31T23:59:59Z"));
        if (ownerId != null) q.setCreatedBy(user(ownerId));
        q.setParticipants(new HashSet<>());
        return q;
    }

    /* ---------------------------- create ---------------------------- */

    @Test
    void create_ok_sets_defaults_and_creator_and_active() {
        mockAuth(7L);

        var req = new QuestDtos.CreateQuestReq(
                "Read 20 pages",
                "Daily reading",
                QuestCategory.OTHER,
                Instant.parse("2025-01-10T00:00:00Z"),
                Instant.parse("2025-02-10T23:59:59Z"),
                QuestVisibility.PRIVATE,
                7L
        );

        User creator = user(7L);

        when(users.findById(7L)).thenReturn(Optional.of(creator));
        when(mapper.toEntity(req)).thenAnswer(inv -> {
            Quest e = new Quest();
            e.setTitle(req.title());
            e.setDescription(req.description());
            e.setCategory(req.category());
            e.setStartDate(req.startDate());
            e.setEndDate(req.endDate());
            e.setVisibility(req.visibility());
            e.setParticipants(new HashSet<>());
            return e;
        });

        QuestDtos.QuestRes out = service.create(req);

        assertThat(out.id()).isNotNull();
        verify(quests).save(argThat(q ->
                q.getCreatedBy() == creator &&
                        q.getStatus() == QuestStatus.ACTIVE &&
                        q.getCategory() == QuestCategory.OTHER &&
                        q.getVisibility() == QuestVisibility.PRIVATE
        ));
    }

    @Test
    void create_unauthenticated_denied() {
        var req = new QuestDtos.CreateQuestReq(
                "A","B",
                QuestCategory.OTHER,
                Instant.now(), Instant.now().plusSeconds(3600),
                QuestVisibility.PRIVATE,
                1L
        );
        assertThrows(AccessDeniedException.class, () -> service.create(req));
        verifyNoInteractions(users, mapper, quests);
    }

    @Test
    void create_mismatchedCreator_denied() {
        mockAuth(7L);
        var req = new QuestDtos.CreateQuestReq(
                "A","B",
                QuestCategory.OTHER,
                Instant.now(), Instant.now().plusSeconds(3600),
                QuestVisibility.PRIVATE,
                99L
        );
        assertThrows(AccessDeniedException.class, () -> service.create(req));
        verifyNoInteractions(users, mapper, quests);
    }

    @Test
    void create_missingUser_404() {
        mockAuth(7L);
        var req = new QuestDtos.CreateQuestReq(
                "A","B",
                QuestCategory.OTHER,
                Instant.now(), Instant.now().plusSeconds(3600),
                QuestVisibility.PRIVATE,
                7L
        );
        when(users.findById(7L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.create(req));
        verify(mapper, never()).toEntity(any());
        verify(quests, never()).save(any());
    }

    /* ---------------------------- list/search/get ---------------------------- */

    @Test
    void list_withStatus_maps_and_marksCompletion() {
        mockAuth(5L);
        Quest q = quest(1L, 2L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);

        when(quests.findByStatus(eq(QuestStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(q)));
        when(questCompletions.existsByQuest_IdAndUser_IdAndStatus(eq(1L), eq(5L),
                eq(QuestCompletion.CompletionStatus.COMPLETED))).thenReturn(true);

        Page<QuestDtos.QuestRes> page = service.list(QuestStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(mapper).toRes(q);
    }

    @Test
    void list_withoutStatus_calls_findAll() {
        when(quests.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(null, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void search_titleContains_ignoreCase() {
        when(quests.findByTitleContainingIgnoreCase(eq("run"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.search("run", PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void search_blank_returns_empty_without_repo_call() {
        Page<QuestDtos.QuestRes> page = service.search("   ", PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
        verify(quests, never()).findByTitleContainingIgnoreCase(anyString(), any());
    }

    @Test
    void getOrThrow_happy() {
        Quest q = quest(9L, 1L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(9L)).thenReturn(Optional.of(q));
        assertThat(service.getOrThrow(9L)).isNotNull();
    }

    @Test
    void getOrThrow_404() {
        when(quests.findById(71L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getOrThrow(71L));
    }

    /* ---------------------------- update ---------------------------- */

    @Test
    void update_owner_ok_sets_fields_and_saves() {
        mockAuth(3L);
        Quest q = quest(10L, 3L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(10L)).thenReturn(Optional.of(q));

        var req = new QuestDtos.UpdateQuestReq(
                "  New Title  ",
                " New Desc ",
                QuestCategory.OTHER,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T23:59:59Z"),
                QuestVisibility.PRIVATE
        );

        QuestDtos.QuestRes out = service.update(10L, req);
        assertNotNull(out);
        assertThat(q.getTitle()).isEqualTo("New Title");
        assertThat(q.getDescription()).isEqualTo("New Desc");
        assertThat(q.getVisibility()).isEqualTo(QuestVisibility.PRIVATE);
        verify(quests).save(q);
    }

    @Test
    void update_admin_ok_even_if_not_owner() {
        mockAuth(99L, "ADMIN");
        Quest q = quest(10L, 3L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(10L)).thenReturn(Optional.of(q));

        var req = new QuestDtos.UpdateQuestReq("T","D", QuestCategory.OTHER, null, null, QuestVisibility.PUBLIC);
        QuestDtos.QuestRes out = service.update(10L, req);

        assertNotNull(out);
        assertThat(q.getVisibility()).isEqualTo(QuestVisibility.PUBLIC);
        verify(quests).save(q);
    }

    @Test
    void update_nonOwner_nonAdmin_denied() {
        mockAuth(5L);
        Quest q = quest(10L, 3L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(10L)).thenReturn(Optional.of(q));
        var req = new QuestDtos.UpdateQuestReq("T","D", QuestCategory.OTHER, null, null, QuestVisibility.PUBLIC);
        assertThrows(AccessDeniedException.class, () -> service.update(10L, req));
        verify(quests, never()).save(any());
    }

    @Test
    void update_owner_but_completed_denied_for_non_admin() {
        mockAuth(3L);
        Quest q = quest(10L, 3L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(10L)).thenReturn(Optional.of(q));
        when(questCompletions.existsByQuest_IdAndUser_IdAndStatus(eq(10L), eq(3L),
                eq(QuestCompletion.CompletionStatus.COMPLETED))).thenReturn(true);

        var req = new QuestDtos.UpdateQuestReq("T","D", QuestCategory.OTHER, null, null, QuestVisibility.PUBLIC);
        assertThrows(AccessDeniedException.class, () -> service.update(10L, req));
        verify(quests, never()).save(any());
    }

    @Test
    void update_admin_can_edit_even_if_completed() {
        mockAuth(3L, "ADMIN");
        Quest q = quest(10L, 3L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(10L)).thenReturn(Optional.of(q));
        when(questCompletions.existsByQuest_IdAndUser_IdAndStatus(eq(10L), eq(3L),
                eq(QuestCompletion.CompletionStatus.COMPLETED))).thenReturn(true);

        var req = new QuestDtos.UpdateQuestReq("T","D", QuestCategory.OTHER, null, null, QuestVisibility.PUBLIC);
        assertNotNull(service.update(10L, req));
        verify(quests).save(q);
    }

    /* ---------------------------- canEditQuest ---------------------------- */

    @Test
    void canEditQuest_owner_and_notCompleted_true() {
        mockAuth(2L);
        Quest q = quest(11L, 2L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(11L)).thenReturn(Optional.of(q));

        assertTrue(service.canEditQuest(11L, SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void canEditQuest_owner_but_completed_false() {
        mockAuth(2L);
        Quest q = quest(11L, 2L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(11L)).thenReturn(Optional.of(q));
        when(questCompletions.existsByQuest_IdAndUser_IdAndStatus(eq(11L), eq(2L),
                eq(QuestCompletion.CompletionStatus.COMPLETED))).thenReturn(true);

        assertFalse(service.canEditQuest(11L, SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void canEditQuest_notOwner_false() {
        mockAuth(7L);
        Quest q = quest(11L, 2L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(11L)).thenReturn(Optional.of(q));
        assertFalse(service.canEditQuest(11L, SecurityContextHolder.getContext().getAuthentication()));
    }

    /* ---------------------------- listMine ---------------------------- */

    @Test
    void listMine_requires_login() {
        assertThrows(AccessDeniedException.class, () -> service.listMine(PageRequest.of(0, 10)));
    }

    @Test
    void listMine_happy() {
        mockAuth(5L);
        when(quests.findByCreatedBy_IdOrParticipants_Id(eq(5L), eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.listMine(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    /* ---------------------------- listDiscover ---------------------------- */

    @Test
    void listDiscover_anonymous_returns_public_active_unfiltered() {
        Quest q1 = quest(1L, 10L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        Quest q2 = quest(2L, 11L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);

        when(quests.findByVisibilityAndStatus(eq(QuestVisibility.PUBLIC), eq(QuestStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(q1, q2)));

        Page<QuestDtos.QuestRes> page = service.listDiscover(PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void listDiscover_authenticated_filters_out_owned_and_already_joined() {
        mockAuth(5L);
        Quest owned = quest(1L, 5L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        Quest joined = quest(2L, 7L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        joined.getParticipants().add(user(5L));
        Quest other = quest(3L, 8L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);

        when(quests.findByVisibilityAndStatus(eq(QuestVisibility.PUBLIC), eq(QuestStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(owned, joined, other)));

        Page<QuestDtos.QuestRes> page = service.listDiscover(PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(3L);
    }

    /* ---------------------------- updateStatus ---------------------------- */

    @Test
    void updateStatus_only_owner_allowed() {
        mockAuth(9L);
        Quest q = quest(4L, 1L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(4L)).thenReturn(Optional.of(q));
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(4L, QuestStatus.ARCHIVED));
    }

    @Test
    void updateStatus_owner_ok_and_saves() {
        mockAuth(1L);
        Quest q = quest(4L, 1L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(4L)).thenReturn(Optional.of(q));

        QuestDtos.QuestRes out = service.updateStatus(4L, QuestStatus.COMPLETED);
        assertNotNull(out);
        assertThat(q.getStatus()).isEqualTo(QuestStatus.COMPLETED);
        verify(quests).save(q);
    }

    @Test
    void updateStatus_missing_denied_first_via_isOwner() {
        mockAuth(1L);
        when(quests.findById(404L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(404L, QuestStatus.ARCHIVED));
    }

    /* ---------------------------- join/leave ---------------------------- */

    @Test
    void join_requires_login() {
        assertThrows(AccessDeniedException.class, () -> service.join(1L));
    }

    @Test
    void join_missing_quest_404() {
        mockAuth(2L);
        when(quests.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.join(1L));
    }

    @Test
    void join_private_forbidden() {
        mockAuth(2L);
        Quest q = quest(1L, 7L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(1L)).thenReturn(Optional.of(q));
        assertThrows(AccessDeniedException.class, () -> service.join(1L));
    }

    @Test
    void join_idempotent_if_already_participant() {
        mockAuth(5L);
        Quest q = quest(1L, 7L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        q.getParticipants().add(user(5L));
        when(quests.findById(1L)).thenReturn(Optional.of(q));
        // service.join loads current user—stub to avoid NotFound
        when(users.findById(5L)).thenReturn(Optional.of(user(5L)));

        QuestDtos.QuestRes out = service.join(1L);
        assertNotNull(out);
        verify(quests, never()).save(any());
    }

    @Test
    void join_adds_current_user_and_saves() {
        mockAuth(5L);
        Quest q = quest(1L, 7L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(1L)).thenReturn(Optional.of(q));
        when(users.findById(5L)).thenReturn(Optional.of(user(5L)));

        QuestDtos.QuestRes out = service.join(1L);
        assertNotNull(out);
        assertThat(q.getParticipants()).extracting("id").contains(5L);
        verify(quests).save(q);
    }

    @Test
    void leave_requires_login() {
        assertThrows(AccessDeniedException.class, () -> service.leave(1L));
    }

    @Test
    void leave_missing_quest_404() {
        mockAuth(2L);
        when(quests.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.leave(1L));
    }

    @Test
    void leave_removes_participant_if_present() {
        mockAuth(5L);
        Quest q = quest(1L, 7L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        q.getParticipants().add(user(5L));
        q.getParticipants().add(user(8L));
        when(quests.findById(1L)).thenReturn(Optional.of(q));

        QuestDtos.QuestRes out = service.leave(1L);
        assertNotNull(out);
        assertThat(q.getParticipants()).extracting("id").doesNotContain(5L);
        verify(quests).save(q);
    }

    @Test
    void leave_noop_when_not_participant() {
        mockAuth(5L);
        Quest q = quest(1L, 7L, QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        q.getParticipants().add(user(8L));
        when(quests.findById(1L)).thenReturn(Optional.of(q));

        QuestDtos.QuestRes out = service.leave(1L);
        assertNotNull(out);
        assertThat(q.getParticipants()).extracting("id").containsExactly(8L);
        verify(quests).save(q);
    }

    /* ---------------------------- archive ---------------------------- */

    @Test
    void archive_owner_ok() {
        mockAuth(3L);
        Quest q = quest(8L, 3L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(8L)).thenReturn(Optional.of(q));

        QuestDtos.QuestRes out = service.archive(8L);
        assertNotNull(out);
        assertThat(q.getStatus()).isEqualTo(QuestStatus.ARCHIVED);
        verify(quests).save(q);
    }

    @Test
    void archive_admin_ok() {
        mockAuth(7L, "ADMIN");
        Quest q = quest(8L, 3L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(8L)).thenReturn(Optional.of(q));

        assertNotNull(service.archive(8L));
        assertThat(q.getStatus()).isEqualTo(QuestStatus.ARCHIVED);
    }

    @Test
    void archive_nonOwner_nonElevated_denied() {
        mockAuth(7L);
        Quest q = quest(8L, 3L, QuestStatus.ACTIVE, QuestVisibility.PRIVATE);
        when(quests.findById(8L)).thenReturn(Optional.of(q));
        assertThrows(AccessDeniedException.class, () -> service.archive(8L));
    }
}
