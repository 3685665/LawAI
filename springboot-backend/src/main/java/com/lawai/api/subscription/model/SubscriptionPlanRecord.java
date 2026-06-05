package com.lawai.api.subscription.model;

import java.time.OffsetDateTime;
import java.util.List;

public record SubscriptionPlanRecord(
    String id,
    String name,
    String slug,
    String badge,
    String description,
    int monthlyPrice,
    int yearlyPrice,
    String currency,
    String usageLimit,
    String usagePeriod,
    boolean highlighted,
    boolean active,
    int sortOrder,
    List<String> features,
    List<String> lockedFeatures,
    String ctaLabel,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
