package com.questify.domain;

import com.questify.domain.QuestStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;


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
    private QuestStatus status = QuestStatus.DRAFT;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
