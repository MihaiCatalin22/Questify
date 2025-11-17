package com.questify.controller;

import com.questify.service.CompletionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/completions")
public class InternalCompletionController {

    private final CompletionService completionService;

    public InternalCompletionController(CompletionService completionService) {
        this.completionService = completionService;
    }

    @GetMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> check(@RequestParam("questId") Long questId,
                                     @RequestParam("userId") String userId) {
        boolean completed = completionService.isCompleted(questId, userId);
        return Map.of("questId", questId, "userId", userId, "completed", completed);
    }

    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> count(@RequestParam("questId") Long questId) {
        long count = completionService.countForQuest(questId);
        return Map.of("questId", questId, "count", count);
    }
}
