package com.questify.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "quest_completions",
        indexes = {
                @Index(name="idx_comp_user", columnList="user_id"),
                @Index(name="idx_comp_quest", columnList="quest_id")
        }
)
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor @Builder
public class QuestCompletion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quest_id", nullable = false)
    private Long questId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false)
    @Builder.Default
    private Instant completedAt = Instant.now();
}
