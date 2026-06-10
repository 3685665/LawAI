package com.lawai.document.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentSearchRequest(
    @NotBlank String query,
    Integer limit
) {
}
