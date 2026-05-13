package com.questify.repository;

import com.questify.domain.StreakActivityDay;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakActivityDayRepository extends JpaRepository<StreakActivityDay, Long> {
    Optional<StreakActivityDay> findByUserIdAndActivityDate(String userId, LocalDate activityDate);

    boolean existsByUserIdAndActivityDate(String userId, LocalDate activityDate);
}
