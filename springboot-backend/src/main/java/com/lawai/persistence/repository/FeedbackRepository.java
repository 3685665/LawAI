package com.lawai.persistence.repository;

import com.lawai.persistence.entity.FeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, String> {

  List<FeedbackEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
