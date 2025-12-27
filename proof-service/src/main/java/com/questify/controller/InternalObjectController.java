package com.questify.controller;

import com.questify.service.ProofStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/objects")
@RequiredArgsConstructor
public class InternalObjectController {

    private final ProofStorageService storage;
    @Value("${internal.token}") private String internalToken;

    @DeleteMapping
    public ResponseEntity<?> delete(@RequestHeader("X-Internal-Token") String token,
                                    @RequestParam String key) throws Exception {
        if (!internalToken.equals(token)) return ResponseEntity.status(403).build();
        storage.delete(key);
        return ResponseEntity.ok(Map.of("deleted", key));
    }
}
