package com.questify.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(
        name = "quest_participants",
        uniqueConstraints = @UniqueConstraint(name = "uq_quest_participant", columnNames = {"quest_id","user_id"})
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    public QuestParticipant(Quest quest, String userId) {
        this.quest = quest;
        this.userId = userId;
        this.joinedAt = Instant.now();
    }
}
