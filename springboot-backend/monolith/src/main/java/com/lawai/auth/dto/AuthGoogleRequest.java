package com.lawai.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthGoogleRequest(
    @NotBlank String credential
) {
}
