package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthForgotPasswordRequest(@Email @NotBlank String email) {
}
