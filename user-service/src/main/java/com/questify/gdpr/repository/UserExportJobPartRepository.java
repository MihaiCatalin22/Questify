package com.questify.gdpr.repository;

import com.questify.gdpr.model.UserExportJobPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserExportJobPartRepository extends JpaRepository<UserExportJobPart, Long> {
    List<UserExportJobPart> findByJobId(String jobId);
    Optional<UserExportJobPart> findByJobIdAndService(String jobId, String service);
    long countByJobIdAndReceivedTrue(String jobId);
}