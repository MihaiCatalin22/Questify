package com.questify.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_profiles",
        indexes = {
                @Index(name="idx_up_username", columnList="username"),
                @Index(name="idx_up_display_name", columnList="displayName")
        })
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor @Builder
public class UserProfile {

    @Id
    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Size(max = 64)
    @Column(unique = false, length = 64)
    private String username;

    @Size(max = 128)
    private String displayName;

    @Email
    @Size(max = 256)
    private String email;

    @Size(max = 512)
    private String avatarUrl;

    @Size(max = 512)
    private String bio;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist void onCreate(){ var now=Instant.now(); createdAt=now; updatedAt=now; }
    @PreUpdate  void onUpdate(){ updatedAt=Instant.now(); }
}
