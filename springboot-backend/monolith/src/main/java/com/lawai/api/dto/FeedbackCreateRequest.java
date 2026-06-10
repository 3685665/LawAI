package com.lawai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackCreateRequest(
    @NotBlank String type,
    @NotBlank @Size(max = 120) String subject,
    @NotBlank @Size(max = 5000) String message
) {
}
