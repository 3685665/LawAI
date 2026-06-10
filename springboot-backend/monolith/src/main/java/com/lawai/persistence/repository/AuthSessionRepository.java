package com.lawai.persistence.repository;

import com.lawai.persistence.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {

  List<AuthSessionEntity> findByExpiresAtAfter(OffsetDateTime expiresAt);

  void deleteByExpiresAtBefore(OffsetDateTime expiresAt);

  void deleteByUserId(String userId);
}
