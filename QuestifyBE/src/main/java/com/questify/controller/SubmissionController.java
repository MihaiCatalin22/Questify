package com.questify.controller;

import com.questify.config.security.CustomUserDetails;
import com.questify.domain.ReviewStatus;
import com.questify.dto.SubmissionDtos.*;
import com.questify.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "https://localhost:5173")
@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService service;
    public SubmissionController(SubmissionService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<SubmissionRes> list(
            @RequestParam(required = false) ReviewStatus status,
            @org.springdoc.core.annotations.ParameterObject Pageable pageable,
            Authentication authentication) {

        if (hasAnyRole(authentication, "ADMIN", "REVIEWER")) {
            return service.listAll(status, pageable);
        }
        Long currentUserId = currentUserId(authentication);
        return service.listForUser(currentUserId, pageable);
    }

    @PostMapping
    public SubmissionRes create(@Valid @RequestBody CreateSubmissionReq req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public SubmissionRes get(@PathVariable Long id) { return service.getOrThrow(id); }

    @GetMapping("/by-quest/{questId}")
    public Page<SubmissionRes> listForQuest(@PathVariable Long questId,
                                            @RequestParam(required = false) ReviewStatus status,
                                            @org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        return service.listForQuest(questId, status, pageable);
    }

    @GetMapping("/by-user/{userId}")
    public Page<SubmissionRes> listForUser(@PathVariable Long userId,
                                           @org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        return service.listForUser(userId, pageable);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyAuthority('REVIEWER', 'ADMIN')")
    public SubmissionRes review(@PathVariable Long id, @Valid @RequestBody ReviewSubmissionReq req) {
        return service.review(id, req);
    }


    @GetMapping(path = "/../quests/{questId}/submissions")
    @PreAuthorize("isAuthenticated()")
    public Page<SubmissionRes> listForQuestFriendly(@PathVariable Long questId,
                                                    @RequestParam(required = false) ReviewStatus status,
                                                    @org.springdoc.core.annotations.ParameterObject Pageable pageable,
                                                    Authentication authentication) {
        return service.listForQuest(questId, status, pageable);
    }

    private boolean hasAnyRole(Authentication auth, String... roles) {
        if (auth == null || auth.getAuthorities() == null) return false;
        var set = auth.getAuthorities();
        for (String r : roles) {
            String role = r.startsWith("ROLE_") ? r : ("ROLE_" + r);
            boolean ok = set.stream().anyMatch(a -> a.getAuthority().equals(role) || a.getAuthority().equals(r));
            if (ok) return true;
        }
        return false;
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof CustomUserDetails cud) return cud.getId();
        return null;
    }
}
