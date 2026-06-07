package com.lawai.api.subscription.dto;

public record BillingCheckoutResponse(
    String checkoutUrl,
    String checkoutSessionId,
    UserSubscriptionDto subscription
) {
}
