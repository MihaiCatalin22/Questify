package com.questify.gdpr.repository;

import com.questify.gdpr.model.UserExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserExportJobRepository extends JpaRepository<UserExportJob, String> {
}
