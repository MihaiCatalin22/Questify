package com.questify.dto;

import java.time.Instant;

public record ParticipantResponse(Long id, String userId, Instant joinedAt) {}
