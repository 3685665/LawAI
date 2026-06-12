package com.lawai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 10, message = "{validation.password.size.min}") String newPassword
) {
}
