package com.questify.repository;

import com.questify.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    List<UserProfile> findTop20ByUsernameStartingWithIgnoreCaseOrDisplayNameStartingWithIgnoreCase(String u, String d);
    long deleteByDeletedAtBefore(Instant cutoff);
    List<UserProfile> findTop200ByDeletedAtIsNotNullOrderByDeletedAtDesc();

}
