package com.lawai.persistence.repository;

import com.lawai.persistence.entity.UserSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscriptionEntity, String> {

  List<UserSubscriptionEntity> findByUserId(String userId);

  Optional<UserSubscriptionEntity> findFirstByUserIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(String userId, String status);
}
