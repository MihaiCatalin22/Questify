package com.questify.service;

import com.questify.config.NotFoundException;
import com.questify.config.security.CustomUserDetails;
import com.questify.domain.*;
import com.questify.dto.QuestDtos;
import com.questify.mapper.QuestMapper;
import com.questify.persistence.QuestCompletionRepository;
import com.questify.persistence.QuestRepository;
import com.questify.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QuestService {

    private final QuestRepository quests;
    private final UserRepository users;
    private final QuestMapper mapper;
    private final QuestCompletionRepository questCompletions;

    public QuestService(QuestRepository quests, UserRepository users, QuestMapper mapper,
                        QuestCompletionRepository questCompletions) {
        this.quests = quests;
        this.users = users;
        this.mapper = mapper;
        this.questCompletions = questCompletions;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) return null;
        return cud.getId();
    }

    private boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String ga = a.getAuthority();
            for (String r : roles) {
                if (("ROLE_" + r).equals(ga)) return true;
            }
            return false;
        });
    }

    public boolean isOwner(Long questId, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return false;
        Object p = authentication.getPrincipal();
        if (!(p instanceof CustomUserDetails cud)) return false;
        return quests.findById(questId)
                .map(q -> q.getCreatedBy() != null && q.getCreatedBy().getId().equals(cud.getId()))
                .orElse(false);
    }

    public QuestDtos.QuestRes create(@Valid QuestDtos.CreateQuestReq req) {
        Long me = currentUserId();
        if (me == null) throw new AccessDeniedException("Login required.");
        if (!me.equals(req.createdByUserId())) {
            throw new AccessDeniedException("You can only create quests for yourself.");
        }

        User creator = users.findById(req.createdByUserId())
                .orElseThrow(() -> new NotFoundException("User not found: " + req.createdByUserId()));

        Quest q = mapper.toEntity(req);
        q.setCreatedBy(creator);
        q.setStatus(QuestStatus.ACTIVE);
        if (q.getCategory() == null) q.setCategory(QuestCategory.OTHER);
        if (q.getVisibility() == null) q.setVisibility(QuestVisibility.PRIVATE);

        Quest saved = quests.save(q);
        return withCompletion(mapper.toRes(saved), false);
    }

    @Transactional(readOnly = true)
    public Page<QuestDtos.QuestRes> list(QuestStatus status, Pageable pageable) {
        Long me = currentUserId();
        Page<Quest> page = (status == null) ? quests.findAll(pageable) : quests.findByStatus(status, pageable);
        return page.map(q -> withCompletion(mapper.toRes(q), isCompletedBy(me, q.getId())));
    }

    @Transactional(readOnly = true)
    public Page<QuestDtos.QuestRes> listByCreator(Long userId, Pageable pageable) {
        Long me = currentUserId();
        return quests.findByCreatedBy_Id(userId, pageable)
                .map(q -> withCompletion(mapper.toRes(q), isCompletedBy(me, q.getId())));
    }

    @Transactional(readOnly = true)
    public Page<QuestDtos.QuestRes> search(String query, Pageable pageable) {
        Long me = currentUserId();
        if (query == null || query.isBlank()) return Page.empty(pageable);
        return quests.findByTitleContainingIgnoreCase(query.trim(), pageable)
                .map(q -> withCompletion(mapper.toRes(q), isCompletedBy(me, q.getId())));
    }

    @Transactional(readOnly = true)
    public QuestDtos.QuestRes getOrThrow(Long id) {
        Long me = currentUserId();
        Quest q = quests.findById(id)
                .orElseThrow(() -> new NotFoundException("Quest not found: " + id));
        return withCompletion(mapper.toRes(q), isCompletedBy(me, id));
    }

    public QuestDtos.QuestRes update(Long id, @Valid QuestDtos.UpdateQuestReq req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean owner = isOwner(id, auth);
        boolean admin = hasAnyRole("ADMIN","REVIEWER");
        if (!(owner || admin)) throw new AccessDeniedException("Only owner or admin can edit.");

        Long me = currentUserId();
        if (isCompletedBy(me, id) && !admin) {
            throw new AccessDeniedException("You’ve completed this quest and can’t edit it anymore.");
        }

        Quest q = quests.findById(id).orElseThrow(() -> new NotFoundException("Quest not found: " + id));
        q.setTitle(req.title().trim());
        q.setDescription(req.description().trim());
        q.setCategory(req.category() != null ? req.category() : QuestCategory.OTHER);
        q.setStartDate(req.startDate());
        q.setEndDate(req.endDate());
        q.setVisibility(req.visibility() != null ? req.visibility() : QuestVisibility.PRIVATE);

        Quest saved = quests.save(q);
        return withCompletion(mapper.toRes(saved), isCompletedBy(me, id));
    }

    public boolean canEditQuest(Long questId, Authentication authentication) {
        if (!isOwner(questId, authentication)) return false;
        Long me = null;
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails cud) {
            me = cud.getId();
        }
        return !isCompletedBy(me, questId);
    }

    @Transactional(readOnly = true)
    public Page<QuestDtos.QuestRes> listMine(Pageable pageable) {
        Long me = currentUserId();
        if (me == null) throw new AccessDeniedException("Login required.");
        Page<Quest> page = quests.findByCreatedBy_IdOrParticipants_Id(me, me, pageable);
        return page.map(q -> withCompletion(mapper.toRes(q), isCompletedBy(me, q.getId())));
    }

    @Transactional(readOnly = true)
    public Page<QuestDtos.QuestRes> listDiscover(Pageable pageable) {
        Long me = currentUserId();
        Page<Quest> page = quests.findByVisibilityAndStatus(QuestVisibility.PUBLIC, QuestStatus.ACTIVE, pageable);

        if (me == null) {
            return page.map(q -> withCompletion(mapper.toRes(q), false));
        }

        var filtered = page.getContent().stream()
                .filter(q -> q.getCreatedBy() == null || !q.getCreatedBy().getId().equals(me))
                .filter(q -> q.getParticipants() == null || q.getParticipants().stream().noneMatch(u -> u.getId().equals(me)))
                .toList();

        var mapped = filtered.stream()
                .map(q -> withCompletion(mapper.toRes(q), isCompletedBy(me, q.getId())))
                .toList();

        return new org.springframework.data.domain.PageImpl<>(mapped, pageable, mapped.size());
    }

    public QuestDtos.QuestRes updateStatus(Long id, QuestStatus status) {
        if (!isOwner(id, SecurityContextHolder.getContext().getAuthentication())) {
            throw new AccessDeniedException("Only the owner can update status.");
        }
        Quest q = quests.findById(id).orElseThrow(() -> new NotFoundException("Quest not found: " + id));
        q.setStatus(status);
        Quest saved = quests.save(q);
        Long me = currentUserId();
        return withCompletion(mapper.toRes(saved), isCompletedBy(me, id));
    }

    public QuestDtos.QuestRes join(Long questId) {
        Long me = currentUserId();
        if (me == null) throw new AccessDeniedException("Login required.");
        Quest q = quests.findById(questId).orElseThrow(() -> new NotFoundException("Quest not found: " + questId));
        if (q.getVisibility() != QuestVisibility.PUBLIC) throw new AccessDeniedException("This quest is private.");
        User u = users.findById(me).orElseThrow(() -> new NotFoundException("User not found: " + me));
        if (q.getParticipants() != null && q.getParticipants().stream().anyMatch(p -> p.getId().equals(me))) {
            return withCompletion(mapper.toRes(q), isCompletedBy(me, questId));
        }
        q.getParticipants().add(u);
        Quest saved = quests.save(q);
        return withCompletion(mapper.toRes(saved), isCompletedBy(me, questId));
    }

    public QuestDtos.QuestRes leave(Long questId) {
        Long me = currentUserId();
        if (me == null) throw new AccessDeniedException("Login required.");
        Quest q = quests.findById(questId).orElseThrow(() -> new NotFoundException("Quest not found: " + questId));
        if (q.getParticipants() != null) {
            q.getParticipants().removeIf(u -> u.getId().equals(me));
        }
        Quest saved = quests.save(q);
        return withCompletion(mapper.toRes(saved), isCompletedBy(me, questId));
    }

    public QuestDtos.QuestRes archive(Long id) {
        boolean owner = isOwner(id, SecurityContextHolder.getContext().getAuthentication());
        boolean elevated = hasAnyRole("ADMIN", "REVIEWER");
        if (!(owner || elevated)) {
            throw new AccessDeniedException("Forbidden.");
        }
        Quest q = quests.findById(id).orElseThrow(() -> new NotFoundException("Quest not found: " + id));
        q.setStatus(QuestStatus.ARCHIVED);
        Quest saved = quests.save(q);
        Long me = currentUserId();
        return withCompletion(mapper.toRes(saved), isCompletedBy(me, id));
    }

    private boolean isCompletedBy(Long userId, Long questId) {
        if (userId == null || questId == null) return false;
        return questCompletions.existsByQuest_IdAndUser_IdAndStatus(
                questId, userId, QuestCompletion.CompletionStatus.COMPLETED);
    }

    private QuestDtos.QuestRes withCompletion(QuestDtos.QuestRes base, boolean completed) {
        return new QuestDtos.QuestRes(
                base.id(),
                base.title(),
                base.description(),
                base.category(),
                base.status(),
                base.startDate(),
                base.endDate(),
                base.createdAt(),
                base.updatedAt(),
                base.createdById(),
                base.participantsCount(),
                completed,
                base.visibility()
        );
    }
}
