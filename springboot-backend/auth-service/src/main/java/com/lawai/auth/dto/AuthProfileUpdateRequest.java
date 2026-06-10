package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthProfileUpdateRequest(
    @NotBlank(message = "Ad soyad gerekli.")
    @Size(max = 120, message = "Ad soyad en fazla 120 karakter olabilir.")
    String name,

    @NotBlank(message = "E-posta gerekli.")
    @Email(message = "Gecerli bir e-posta adresi girin.")
    @Size(max = 180, message = "E-posta en fazla 180 karakter olabilir.")
    String email
) {
}
