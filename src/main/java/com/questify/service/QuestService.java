package com.questify.service;

import com.questify.config.NotFoundException;
import com.questify.domain.Quest;
import com.questify.domain.User;
import com.questify.domain.QuestStatus;
import com.questify.dto.QuestDtos;
import com.questify.mapper.QuestMapper;
import com.questify.persistence.QuestRepository;
import com.questify.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public QuestDtos.QuestRes create (@Valid QuestDtos.CreateQuestReq req) {
        User creator = users.findById(req.createdByUserId())
                .orElseThrow(() -> new NotFoundException("User not found: " + req.createdByUserId()));

        Quest q = mapper.toEntity(req);
        q.setCreatedBy(creator);
        q.setStatus(QuestStatus.ACTIVE);
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
                .orElseThrow(()-> new NotFoundException("Quest not found: " + id)));
    }

    public QuestDtos.QuestRes updateStatus(Long id, QuestStatus status) {
        Quest q = quests.findById(id).orElseThrow(()-> new NotFoundException("Quest not found: " + id));
        q.setStatus(status);
        return mapper.toRes(q);
    }

    public QuestDtos.QuestRes archive(Long id) {
        var q = quests.findById(id).orElseThrow(() -> new NotFoundException("Quest not found: " + id));
        q.setStatus(QuestStatus.ARCHIVED);
        return mapper.toRes(q);
    }
}
