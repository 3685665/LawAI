package com.lawai.api.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record UserSubscriptionStatusRequest(
    @NotBlank String status
) {
}
