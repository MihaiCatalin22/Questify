package com.questify.service;

import com.questify.config.NotFoundException;
import com.questify.config.security.CustomUserDetails;
import com.questify.domain.Quest;
import com.questify.domain.QuestCategory;
import com.questify.domain.QuestStatus;
import com.questify.domain.User;
import com.questify.dto.QuestDtos;
import com.questify.mapper.QuestMapper;
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

    public QuestService(QuestRepository quests, UserRepository users, QuestMapper mapper) {
        this.quests = quests;
        this.users = users;
        this.mapper = mapper;
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

        return mapper.toRes(quests.save(q));
    }

    @Transactional(readOnly = true)
    public Page<QuestDtos.QuestRes> list(QuestStatus status, Pageable pageable) {
        Page<Quest> page = (status == null) ? quests.findAll(pageable) : quests.findByStatus(status, pageable);
        return page.map(mapper::toRes);
    }

    @Transactional(readOnly = true)
    public Page<QuestDtos.QuestRes> listByCreator(Long userId, Pageable pageable) {
        return quests.findByCreatedBy_Id(userId, pageable).map(mapper::toRes);
    }

    @Transactional(readOnly = true)
    public Page<QuestDtos.QuestRes> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) return Page.empty(pageable);
        return quests.findByTitleContainingIgnoreCase(query.trim(), pageable).map(mapper::toRes);
    }

    @Transactional(readOnly = true)
    public QuestDtos.QuestRes getOrThrow(Long id) {
        return mapper.toRes(quests.findById(id)
                .orElseThrow(() -> new NotFoundException("Quest not found: " + id)));
    }

    public QuestDtos.QuestRes updateStatus(Long id, QuestStatus status) {
        // Owner-only
        if (!isOwner(id, SecurityContextHolder.getContext().getAuthentication())) {
            throw new AccessDeniedException("Only the owner can update status.");
        }
        Quest q = quests.findById(id).orElseThrow(() -> new NotFoundException("Quest not found: " + id));
        q.setStatus(status);
        return mapper.toRes(q);
    }

    public QuestDtos.QuestRes archive(Long id) {
        boolean owner = isOwner(id, SecurityContextHolder.getContext().getAuthentication());
        boolean elevated = hasAnyRole("ADMIN", "REVIEWER");
        if (!(owner || elevated)) {
            throw new AccessDeniedException("Forbidden.");
        }
        Quest q = quests.findById(id).orElseThrow(() -> new NotFoundException("Quest not found: " + id));
        q.setStatus(QuestStatus.ARCHIVED);
        return mapper.toRes(q);
    }
}
