package com.questify.controller;

import com.questify.config.JwtAuth;
import com.questify.domain.Quest;
import com.questify.domain.QuestStatus;
import com.questify.dto.ParticipantResponse;
import com.questify.dto.QuestDtos.*;
import com.questify.dto.QuestSummaryRes;
import com.questify.mapper.QuestMapper;
import com.questify.repository.QuestParticipantRepository;
import com.questify.service.CompletionService;
import com.questify.service.QuestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/quests")
public class QuestController {

    private final QuestService service;
    private final QuestParticipantRepository participantRepo;
    private final CompletionService completionService;
    private final JwtAuth jwt;

    public QuestController(QuestService service,
                           QuestParticipantRepository participantRepo,
                           CompletionService completionService,
                           JwtAuth jwt) {
        this.service = service;
        this.participantRepo = participantRepo;
        this.completionService = completionService;
        this.jwt = jwt;
    }

    private PageRequest page(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
        return PageRequest.of(p, s, sort);
    }

    @GetMapping("/mine-or-participating/summary")
    @PreAuthorize("isAuthenticated()")
    public QuestSummaryRes mineOrParticipatingSummary(@RequestParam(required = false) Boolean archived,
                                                      Authentication auth) {
        var me = jwt.userId(auth);
        long total = service.countMineOrParticipatingFiltered(me, archived);
        long completed = completionService.countCompletedInMineOrParticipatingFiltered(me, archived);
        return new QuestSummaryRes(total, completed);
    }


    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<QuestRes> create(@Valid @RequestBody CreateQuestReq req, Authentication auth) {
        var me = jwt.userId(auth);
        var saved = service.create(req, me);
        var res = QuestMapper.toRes(saved, service.participantsCount(saved.getId()), false);
        return ResponseEntity.created(URI.create("/quests/" + saved.getId())).body(res);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@questSecurity.isOwner(#id, authentication)")
    public QuestRes update(@PathVariable Long id, @Valid @RequestBody UpdateQuestReq req, Authentication auth) {
        var me = jwt.userId(auth);
        var q = service.update(id, req, me);
        boolean completedByMe = completionService.isCompleted(id, me);
        return QuestMapper.toRes(q, service.participantsCount(id), completedByMe);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@questSecurity.isOwner(#id, authentication)")
    public QuestRes updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateQuestStatusReq req, Authentication auth) {
        var me = jwt.userId(auth);
        var q = service.updateStatus(id, req, me);
        boolean completedByMe = completionService.isCompleted(id, me);
        return QuestMapper.toRes(q, service.participantsCount(id), completedByMe);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@questSecurity.isOwner(#id, authentication) or hasAnyRole('ADMIN','REVIEWER')")
    public QuestRes archivePost(@PathVariable Long id, Authentication auth) {
        var me = jwt.userId(auth);
        var q = service.archive(id, me);
        boolean completedByMe = completionService.isCompleted(id, me);
        return QuestMapper.toRes(q, service.participantsCount(id), completedByMe);
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> join(@PathVariable Long id, Authentication auth) {
        service.join(id, jwt.userId(auth));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leave(@PathVariable Long id, Authentication auth) {
        service.leave(id, jwt.userId(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/participants")
    public Page<ParticipantResponse> participants(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        var list = participantRepo.findByQuest_Id(id);
        var dtos = QuestMapper.toParticipantDtos(list);
        var p = page(page, size);
        int from = Math.min(p.getPageNumber() * p.getPageSize(), dtos.size());
        int to = Math.min(from + p.getPageSize(), dtos.size());
        return new PageImpl<>(dtos.subList(from, to), p, dtos.size());
    }

    @GetMapping
    public Page<QuestRes> list(@RequestParam(required = false) QuestStatus status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size) {
        Page<Quest> p = (status == null)
                ? service.discoverActive(page(page, size))
                : service.listByStatus(status, page(page, size));
        return p.map(q -> QuestMapper.toRes(q, service.participantsCount(q.getId()), false));
    }

    @GetMapping("/{id}")
    public QuestRes get(@PathVariable Long id, Authentication auth) {
        var q = service.get(id);
        boolean completedByMe = (auth != null && auth.isAuthenticated())
                ? completionService.isCompleted(id, jwt.userId(auth))
                : false;
        return QuestMapper.toRes(q, service.participantsCount(id), completedByMe);
    }

    @GetMapping("/discover")
    public Page<QuestRes> discover(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        var p = service.discoverActive(page(page, size))
                .map(q -> QuestMapper.toRes(q, service.participantsCount(q.getId()), false));
        return new PageImpl<>(p.getContent(), p.getPageable(), p.getTotalElements());
    }

    @GetMapping("/search")
    public Page<QuestRes> search(@RequestParam String q,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        var p = service.searchPublic(q, page(page, size))
                .map(x -> QuestMapper.toRes(x, service.participantsCount(x.getId()), false));
        return new PageImpl<>(p.getContent(), p.getPageable(), p.getTotalElements());
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public Page<QuestRes> mine(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) QuestStatus status,
                               Authentication auth) {
        var me = jwt.userId(auth);
        Page<Quest> p = (status == null)
                ? service.mine(me, page(page, size))
                : service.mineByStatus(me, status, page(page, size));

        return p.map(x -> QuestMapper.toRes(x, service.participantsCount(x.getId()),
                completionService.isCompleted(x.getId(), me)));
    }

    @GetMapping("/mine-or-participating")
    @PreAuthorize("isAuthenticated()")
    public Page<QuestRes> mineOrParticipating(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) Boolean archived,
                                              @RequestParam(required = false) QuestStatus status,
                                              Authentication auth) {
        var me = jwt.userId(auth);

        Page<Quest> p;
        if (archived != null) {
            p = service.mineOrParticipatingFiltered(me, archived, page(page, size));
        } else if (status != null) {
            if (status == QuestStatus.ARCHIVED) {
                p = service.mineOrParticipatingWithStatus(me, QuestStatus.ARCHIVED, page(page, size));
            } else {
                p = service.mineOrParticipatingWithStatus(me, status, page(page, size));
            }
        } else {
            p = service.mineOrParticipating(me, page(page, size));
        }

        return p.map(x -> QuestMapper.toRes(x, service.participantsCount(x.getId()),
                completionService.isCompleted(x.getId(), me)));
    }
}
