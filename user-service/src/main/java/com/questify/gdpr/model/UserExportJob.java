package com.questify.gdpr.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "user_export_jobs")
public class UserExportJob {

    public enum Status { PENDING, RUNNING, COMPLETED, EXPIRED, FAILED }

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 128)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(length = 1024)
    private String zipObjectKey;

    @Column
    private Instant lastProgressAt;

    @Column
    private Instant failedAt;

    @Column(length = 1024)
    private String failureReason;


    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserExportJobPart> parts = new ArrayList<>();

    @PrePersist
    void ensureId() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
    }
}
