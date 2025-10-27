package com.questify.controller;

import com.questify.domain.QuestStatus;
import com.questify.domain.ReviewStatus;
import com.questify.dto.QuestDtos;
import com.questify.dto.SubmissionDtos.SubmissionRes;
import com.questify.service.QuestService;
import com.questify.service.SubmissionService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "https://localhost:5173")
@RestController
@RequestMapping("/quests")
public class QuestController {
    private final QuestService service;
    private final SubmissionService submissions;

    public QuestController(QuestService service, SubmissionService submissions) {
        this.service = service;
        this.submissions = submissions;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public QuestDtos.QuestRes create (@Valid @RequestBody QuestDtos.CreateQuestReq req) {
        return service.create(req);
    }

    @GetMapping
    public Page<QuestDtos.QuestRes> list (@RequestParam(required = false) QuestStatus status,
                                          @RequestParam (defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.list(status, PageRequest.of(page, size));
    }

    @GetMapping("/by-user/{userId}")
    public Page<QuestDtos.QuestRes> listByCreator(@PathVariable Long userId,
                                                  @ParameterObject Pageable pageable) {
        return service.listByCreator(userId, pageable);
    }

    @GetMapping("/search")
    public Page<QuestDtos.QuestRes> search(@RequestParam String q,
                                           @ParameterObject Pageable pageable) {
        return service.search(q, pageable);
    }

    @GetMapping("/{id}")
    public QuestDtos.QuestRes get (@PathVariable Long id) {
        return service.getOrThrow(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@questService.canEditQuest(#id, authentication)")
    public QuestDtos.QuestRes update(@PathVariable Long id,
                                     @Valid @RequestBody QuestDtos.UpdateQuestReq req) {
        return service.update(id, req);
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public Page<QuestDtos.QuestRes> listMine(@ParameterObject Pageable pageable) {
        return service.listMine(pageable);
    }

    @GetMapping("/discover")
    public Page<QuestDtos.QuestRes> listDiscover(@ParameterObject Pageable pageable) {
        return service.listDiscover(pageable);
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public QuestDtos.QuestRes join(@PathVariable Long id) {
        return service.join(id);
    }

    @DeleteMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public QuestDtos.QuestRes leave(@PathVariable Long id) {
        return service.leave(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@questService.isOwner(#id, authentication)")
    public QuestDtos.QuestRes updateStatus(@PathVariable Long id,
                                           @Valid @RequestBody QuestDtos.UpdateQuestStatusReq req) {
        return service.updateStatus(id, req.status());
    }

    @PreAuthorize("@questService.isOwner(#id, authentication) or hasAnyAuthority('ADMIN','REVIEWER')")
    @PostMapping("/{id}/archive")
    public QuestDtos.QuestRes archive(@PathVariable Long id) {
        return service.archive(id);
    }

    @GetMapping("/{questId}/submissions")
    @PreAuthorize("isAuthenticated()")
    public Page<SubmissionRes> submissionsForQuest(@PathVariable Long questId,
                                                   @RequestParam(required = false) ReviewStatus status,
                                                   @ParameterObject Pageable pageable) {
        return submissions.listForQuest(questId, status, pageable);
    }
}
