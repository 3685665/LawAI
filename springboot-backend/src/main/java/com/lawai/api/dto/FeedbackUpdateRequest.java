package com.lawai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackUpdateRequest(
    @NotBlank String type,
    @NotBlank @Size(max = 120) String subject,
    @NotBlank @Size(max = 5000) String message,
    @NotBlank String status
) {
}
