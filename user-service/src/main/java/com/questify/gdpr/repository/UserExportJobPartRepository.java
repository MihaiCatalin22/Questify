package com.questify.gdpr.repository;

import com.questify.gdpr.model.UserExportJobPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserExportJobPartRepository extends JpaRepository<UserExportJobPart, Long> {
    List<UserExportJobPart> findByJob_Id(String jobId);
    Optional<UserExportJobPart> findByJob_IdAndService(String jobId, String service);
}
