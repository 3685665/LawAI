package com.lawai.persistence.repository;

import com.lawai.persistence.entity.BillingEventEntity;
import com.lawai.persistence.entity.BillingEventEntity.BillingEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingEventRepository extends JpaRepository<BillingEventEntity, BillingEventId> {

  @Query("""
      SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
      FROM BillingEventEntity e
      WHERE LOWER(e.id.provider) = LOWER(:provider) AND e.id.eventId = :eventId
      """)
  boolean existsByProviderAndEventId(@Param("provider") String provider, @Param("eventId") String eventId);
}
