package com.lawai.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedbackStatusUpdateRequest(
    @NotBlank String status
) {
}
