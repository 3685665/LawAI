package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthForgotPasswordRequest(
    @Email(message = "{validation.email.invalid}") @NotBlank(message = "{validation.email.required}") String email
) {
}
