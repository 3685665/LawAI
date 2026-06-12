package com.lawai.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CaseAiActionRequest(
    @NotBlank String action
) {
}
