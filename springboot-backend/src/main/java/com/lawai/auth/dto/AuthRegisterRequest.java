package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
    @NotBlank String name,
    @Email @NotBlank String email,
    @NotBlank @Size(min = 10, message = "Sifre en az 10 karakter olmali.") String password
) {
}
