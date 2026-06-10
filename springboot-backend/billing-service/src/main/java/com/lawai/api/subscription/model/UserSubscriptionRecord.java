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
    String provider,
    String providerCustomerId,
    String providerSubscriptionId,
    String providerCheckoutSessionId,
    String providerPriceId,
    String lastPaymentStatus,
    boolean cancelAtPeriodEnd,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
  public UserSubscriptionRecord withStatus(String nextStatus, OffsetDateTime updatedAt) {
    return new UserSubscriptionRecord(id, userId, userName, userEmail, planId, planName, billingCycle, nextStatus, provider, providerCustomerId, providerSubscriptionId, providerCheckoutSessionId, providerPriceId, lastPaymentStatus, cancelAtPeriodEnd, startsAt, endsAt, createdAt, updatedAt);
  }

  public UserSubscriptionRecord withUser(String nextName, String nextEmail, OffsetDateTime updatedAt) {
    return new UserSubscriptionRecord(id, userId, nextName, nextEmail, planId, planName, billingCycle, status, provider, providerCustomerId, providerSubscriptionId, providerCheckoutSessionId, providerPriceId, lastPaymentStatus, cancelAtPeriodEnd, startsAt, endsAt, createdAt, updatedAt);
  }

  public UserSubscriptionRecord withPlan(String nextPlanId, String nextPlanName, String nextBillingCycle, OffsetDateTime nextStartsAt, OffsetDateTime nextEndsAt, OffsetDateTime updatedAt) {
    return new UserSubscriptionRecord(id, userId, userName, userEmail, nextPlanId, nextPlanName, nextBillingCycle, status, provider, providerCustomerId, providerSubscriptionId, providerCheckoutSessionId, providerPriceId, lastPaymentStatus, cancelAtPeriodEnd, nextStartsAt, nextEndsAt, createdAt, updatedAt);
  }

  public UserSubscriptionRecord withProvider(String nextProvider, String nextCustomerId, String nextSubscriptionId, String nextCheckoutSessionId, String nextPriceId, String nextPaymentStatus, boolean nextCancelAtPeriodEnd, OffsetDateTime updatedAt) {
    return new UserSubscriptionRecord(id, userId, userName, userEmail, planId, planName, billingCycle, status, nextProvider, nextCustomerId, nextSubscriptionId, nextCheckoutSessionId, nextPriceId, nextPaymentStatus, nextCancelAtPeriodEnd, startsAt, endsAt, createdAt, updatedAt);
  }

  public UserSubscriptionRecord withPeriod(OffsetDateTime nextStartsAt, OffsetDateTime nextEndsAt, OffsetDateTime updatedAt) {
    return new UserSubscriptionRecord(id, userId, userName, userEmail, planId, planName, billingCycle, status, provider, providerCustomerId, providerSubscriptionId, providerCheckoutSessionId, providerPriceId, lastPaymentStatus, cancelAtPeriodEnd, nextStartsAt, nextEndsAt, createdAt, updatedAt);
  }
}
