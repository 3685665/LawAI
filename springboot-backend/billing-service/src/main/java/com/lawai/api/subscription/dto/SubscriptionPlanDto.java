package com.lawai.api.subscription.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SubscriptionPlanDto(
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
    String iyzicoProductRef,
    String iyzicoMonthlyPlanRef,
    String iyzicoYearlyPlanRef,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
