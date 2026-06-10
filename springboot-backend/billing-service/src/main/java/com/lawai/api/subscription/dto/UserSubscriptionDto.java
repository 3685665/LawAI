package com.lawai.api.subscription.dto;

import java.time.OffsetDateTime;

public record UserSubscriptionDto(
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
}
