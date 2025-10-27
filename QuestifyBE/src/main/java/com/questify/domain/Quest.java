package com.questify.domain;

import com.questify.domain.QuestStatus;
import com.questify.domain.QuestCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "quests")
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor @Builder
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

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
    private Instant startDate;
    private Instant endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestVisibility visibility = QuestVisibility.PRIVATE;

    @ManyToMany
    @JoinTable(
            name = "quest_participants",
            joinColumns = @JoinColumn(name = "quest_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> participants = new HashSet<>();

    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
