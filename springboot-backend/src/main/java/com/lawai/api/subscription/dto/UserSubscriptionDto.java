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
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
