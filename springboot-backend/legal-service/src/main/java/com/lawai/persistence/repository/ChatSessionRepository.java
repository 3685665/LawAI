package com.lawai.persistence.repository;

import com.lawai.persistence.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

  List<ChatSessionEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

  Optional<ChatSessionEntity> findByIdAndUserId(String id, String userId);

  void deleteByIdAndUserId(String id, String userId);
}
