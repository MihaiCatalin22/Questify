package com.questify.gdpr.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "user_export_job_parts",
        uniqueConstraints = @UniqueConstraint(name = "uq_export_job_part", columnNames = {"job_id", "service"})
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserExportJobPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="job_id", nullable = false, length = 64)
    private String jobId;

    @Column(nullable = false, length = 64)
    private String service;

    @Column(nullable = false)
    private boolean received;

    private Instant receivedAt;
}
