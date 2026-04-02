package com.questify.controller;

import com.questify.dto.CoachContextDtos.CoachContextRes;
import com.questify.service.CoachContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalCoachContextController {

    private final CoachContextService service;

    @GetMapping("/{userId}/coach-context")
    public CoachContextRes coachContext(@PathVariable String userId,
                                        @RequestParam(defaultValue = "true") boolean includeRecentHistory) {
        return service.getCoachContext(userId, includeRecentHistory);
    }
}
