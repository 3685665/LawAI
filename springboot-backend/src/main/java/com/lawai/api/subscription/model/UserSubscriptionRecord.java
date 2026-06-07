package com.lawai.api.subscription.model;

import java.time.OffsetDateTime;

public record UserSubscriptionRecord(
    String id,
    String userId,
    String userName,
    String userEmail,
    String planId,
    String planName,
    String billingCycle,
    String status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
  public UserSubscriptionRecord withStatus(String nextStatus, OffsetDateTime updatedAt) {
    return new UserSubscriptionRecord(id, userId, userName, userEmail, planId, planName, billingCycle, nextStatus, startsAt, endsAt, createdAt, updatedAt);
  }

  public UserSubscriptionRecord withUser(String nextName, String nextEmail, OffsetDateTime updatedAt) {
    return new UserSubscriptionRecord(id, userId, nextName, nextEmail, planId, planName, billingCycle, status, startsAt, endsAt, createdAt, updatedAt);
  }

  public UserSubscriptionRecord withPlan(String nextPlanId, String nextPlanName, String nextBillingCycle, OffsetDateTime nextStartsAt, OffsetDateTime nextEndsAt, OffsetDateTime updatedAt) {
    return new UserSubscriptionRecord(id, userId, userName, userEmail, nextPlanId, nextPlanName, nextBillingCycle, status, nextStartsAt, nextEndsAt, createdAt, updatedAt);
  }
}
