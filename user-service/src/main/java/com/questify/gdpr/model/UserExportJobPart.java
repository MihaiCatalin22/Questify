package com.questify.gdpr.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(
        name = "user_export_job_parts",
        uniqueConstraints = @UniqueConstraint(name = "uq_job_service", columnNames = {"job_id", "service"})
)
public class UserExportJobPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private UserExportJob job;

    @Column(nullable = false, length = 64)
    private String service;

    @Column(nullable = false)
    private boolean received;

    private Instant receivedAt;
}
