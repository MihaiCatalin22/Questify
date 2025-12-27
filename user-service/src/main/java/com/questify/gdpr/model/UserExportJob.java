package com.questify.gdpr.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_export_jobs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 128)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(length = 512)
    private String zipObjectKey;

    @Column(length = 1024)
    private String errorMessage;

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, EXPIRED }
}
