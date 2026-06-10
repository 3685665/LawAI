package com.lawai.persistence.repository;

import com.lawai.persistence.entity.EmailVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, String> {

  List<EmailVerificationEntity> findByUserIdAndUsedFalse(String userId);

  void deleteByUserIdAndUsedFalse(String userId);
}
