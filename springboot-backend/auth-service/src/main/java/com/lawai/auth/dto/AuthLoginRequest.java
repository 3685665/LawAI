package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
    @Email(message = "{validation.email.invalid}") @NotBlank(message = "{validation.email.required}") String email,
    @NotBlank(message = "{validation.password.required}") String password,
    Boolean rememberMe
) {
}
