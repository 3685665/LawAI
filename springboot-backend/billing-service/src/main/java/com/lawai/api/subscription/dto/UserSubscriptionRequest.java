package com.lawai.api.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record UserSubscriptionRequest(
    @NotBlank String planId,
    @NotBlank String billingCycle
) {
}
