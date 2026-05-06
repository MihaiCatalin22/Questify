package com.questify.controller;

import com.questify.service.CompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/internal/quests")
@RequiredArgsConstructor
public class InternalQuestProgressController {

    private final CompletionService completionService;

    @PostMapping("/{id}/completion")
    public ResponseEntity<Void> markCompleted(@PathVariable("id") Long questId,
                                              @RequestBody Map<String, Object> body) {
        String userId = String.valueOf(body.get("userId"));
        Long submissionId = body.get("submissionId") != null
                ? Long.valueOf(String.valueOf(body.get("submissionId"))) : null;
        Instant submittedAt = body.get("submittedAt") != null
                ? Instant.parse(String.valueOf(body.get("submittedAt"))) : null;

        completionService.upsertCompleted(questId, userId, submissionId, submittedAt);
        return ResponseEntity.noContent().build();
    }
}
