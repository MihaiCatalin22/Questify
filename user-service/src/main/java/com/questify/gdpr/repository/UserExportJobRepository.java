package com.questify.gdpr.repository;

import com.questify.gdpr.model.UserExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface UserExportJobRepository extends JpaRepository<UserExportJob, String> {
    List<UserExportJob> findByStatusInAndExpiresAtBefore(List<UserExportJob.Status> statuses, Instant now);
}
