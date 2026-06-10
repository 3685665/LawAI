package com.lawai.persistence.repository;

import com.lawai.persistence.entity.SubscriptionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, String> {
}
