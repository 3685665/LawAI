package com.lawai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 10, message = "Sifre en az 10 karakter olmali.") String newPassword
) {
}
