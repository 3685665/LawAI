package com.lawai.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthGoogleRequest(
    @NotBlank(message = "{validation.google-credential.required}") String credential
) {
}
