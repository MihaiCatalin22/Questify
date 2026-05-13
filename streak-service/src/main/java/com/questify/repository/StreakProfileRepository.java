package com.questify.repository;

import com.questify.domain.StreakProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakProfileRepository extends JpaRepository<StreakProfile, String> {
}
