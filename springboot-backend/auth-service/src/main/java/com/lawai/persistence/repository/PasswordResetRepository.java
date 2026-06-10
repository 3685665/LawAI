package com.lawai.persistence.repository;

import com.lawai.persistence.entity.PasswordResetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordResetRepository extends JpaRepository<PasswordResetEntity, String> {

  List<PasswordResetEntity> findByUserIdAndUsedFalse(String userId);

  void deleteByUserIdAndUsedFalse(String userId);

  void deleteByUserId(String userId);
}
