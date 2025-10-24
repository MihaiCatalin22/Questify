package com.questify.controller;

import com.questify.domain.QuestStatus;
import com.questify.dto.QuestDtos;
import com.questify.service.QuestService;
import jakarta.validation.Valid;
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

    public QuestController(QuestService service) {
        this.service = service;
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
                                                  @org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        return service.listByCreator(userId, pageable);
    }

    @GetMapping("/search")
    public Page<QuestDtos.QuestRes> search(@RequestParam String q,
                                           @org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        return service.search(q, pageable);
    }

    @GetMapping("/{id}")
    public QuestDtos.QuestRes get (@PathVariable Long id) {
        return service.getOrThrow(id);
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
}
