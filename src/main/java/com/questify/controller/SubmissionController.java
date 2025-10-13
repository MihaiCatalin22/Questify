package com.questify.controller;

import com.questify.domain.ReviewStatus;
import com.questify.dto.SubmissionDtos.*;
import com.questify.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService service;
    public SubmissionController(SubmissionService service) { this.service = service; }


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
    public SubmissionRes review(@PathVariable Long id, @Valid @RequestBody ReviewSubmissionReq req) {
        return service.review(id, req);
    }
}
