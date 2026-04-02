package com.questify.controller;

import com.questify.dto.ProfileDtos.CoachSettingsRes;
import com.questify.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalCoachSettingsController {

    private final UserProfileService service;

    @GetMapping("/{userId}/coach-settings")
    public CoachSettingsRes coachSettings(@PathVariable String userId) {
        return service.getCoachSettings(userId);
    }
}
