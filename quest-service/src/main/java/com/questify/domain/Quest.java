package com.questify.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Builder
@Entity
@Table(name="quests")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class Quest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title must not be empty.")
    @Size(min = 3, max = 140, message = "Title must be between 3 and 140 characters.")
    @Column(nullable = false, length = 140)
    private String title;

    @NotBlank(message = "Description must not be empty.")
    @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters.")
    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private QuestStatus status = QuestStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private QuestCategory category = QuestCategory.OTHER;

    /** Replaces ManyToOne<User>. Store the creator's user id (numeric string for now). */
    @Column(name = "created_by_user_id", nullable = false, length = 128)
    private String createdByUserId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
    private Instant startDate;
    private Instant endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private QuestVisibility visibility = QuestVisibility.PRIVATE;

    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<QuestParticipant> participants = new LinkedHashSet<>();

    @PrePersist void onCreate() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate  void onUpdate() { updatedAt = Instant.now(); }

    public void addParticipant(String userId) {
        if (participants.stream().noneMatch(p -> p.getUserId().equals(userId))) {
            participants.add(new QuestParticipant(this, userId));
        }
    }
    public void removeParticipant(String userId) {
        participants.removeIf(p -> p.getUserId().equals(userId));
    }
}
