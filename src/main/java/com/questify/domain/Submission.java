package com.questify.domain;

import com.questify.domain.ReviewStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.time.Instant;


@Entity
@Table(name = "submissions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Submission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Quest quest;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Size(max = 2000)
    private String proofText;

    @Size(max = 2000)
    @Pattern(regexp = "^(https?://.+)", message = "The proof URL must start with http:// or https://.")
    private String proofUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewStatus reviewStatus;

    @Size(max = 500)
    private String reviewNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist void onCreate() {
        createdAt = Instant.now();
        if (reviewStatus == null) reviewStatus = ReviewStatus.PENDING;
    }
}
