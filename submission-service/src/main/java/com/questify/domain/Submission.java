package com.questify.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "submissions",
        indexes = {
                @Index(name="idx_sub_user", columnList="user_id"),
                @Index(name="idx_sub_quest", columnList="quest_id"),
                @Index(name="idx_sub_status", columnList="status")
        }
)
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor @Builder
public class Submission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "quest_id", nullable = false)
    private Long questId;

    @NotBlank
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @NotBlank
    @Size(max = 512)
    @Column(name = "proof_key", nullable = false, length = 512)
    private String proofKey;

    @Size(max = 2000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.SCANNING;

    @Column(name = "reviewer_user_id", length = 128)
    private String reviewerUserId;

    private Instant reviewedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist void onCreate(){ var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate  void onUpdate(){ updatedAt = Instant.now(); }
}
