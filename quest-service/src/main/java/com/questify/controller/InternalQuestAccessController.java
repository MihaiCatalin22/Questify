package com.questify.controller;

import com.questify.service.QuestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalQuestAccessController {

    private final QuestService questService;


    @GetMapping("/quests/{id}/participants/{userId}/allowed")
    public ResponseEntity<?> allowed(@PathVariable("id") Long questId,
                                     @PathVariable String userId) {
        boolean ok = questService.isOwnerOrParticipant(questId, userId);
        return ResponseEntity.ok(Map.of("allowed", ok));
    }
}
