package com.lawai.api.subscription.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SubscriptionPlanRequest(
    @NotBlank String name,
    String slug,
    String badge,
    String description,
    @Min(0) int monthlyPrice,
    @Min(0) int yearlyPrice,
    String currency,
    String usageLimit,
    String usagePeriod,
    boolean highlighted,
    boolean active,
    int sortOrder,
    @NotNull List<String> features,
    List<String> lockedFeatures,
    String ctaLabel,
    String stripeProductId,
    String stripeMonthlyPriceId,
    String stripeYearlyPriceId
) {
}
