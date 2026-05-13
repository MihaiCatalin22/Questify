package com.questify.controller;

import com.questify.service.StreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/streaks")
@RequiredArgsConstructor
public class StreakController {
    private final StreakService streakService;

    @GetMapping("/me")
    public StreakService.StreakSummary mine(@AuthenticationPrincipal Jwt jwt) {
        return streakService.summary(userId(jwt));
    }

    private String userId(Jwt jwt) {
        String userId = jwt.getClaimAsString("user_id");
        return userId == null || userId.isBlank() ? jwt.getSubject() : userId;
    }
}
