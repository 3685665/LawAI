package com.lawai.api.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingCheckoutRequest(
    @NotBlank String planId,
    @NotBlank String billingCycle
) {
}
