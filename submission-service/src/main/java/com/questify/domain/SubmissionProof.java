package com.questify.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "submission_proofs",
        indexes = {
                @Index(name="idx_sp_submission", columnList="submission_id"),
                @Index(name="idx_sp_scan_status", columnList="scan_status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sp_proof_key", columnNames = "proof_key")
        }
)
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor @Builder
public class SubmissionProof {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @NotBlank
    @Size(max = 512)
    @Column(name = "proof_key", nullable = false, length = 512)
    private String proofKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 16)
    @Builder.Default
    private ProofScanStatus scanStatus = ProofScanStatus.PENDING;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist void onCreate() {
        createdAt = Instant.now();
    }
}
