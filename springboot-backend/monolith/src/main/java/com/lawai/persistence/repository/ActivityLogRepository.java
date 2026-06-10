package com.lawai.persistence.repository;

import com.lawai.persistence.entity.ActivityLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntity, String> {

  List<ActivityLogEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
