package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
    @NotBlank(message = "Ad soyad gerekli.") String name,
    @Email(message = "Gecerli bir e-posta adresi girin.") @NotBlank(message = "E-posta gerekli.") String email,
    @NotBlank(message = "Sifre gerekli.") @Size(min = 10, message = "Sifre en az 10 karakter olmali.") String password
) {
  public AuthRegisterRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim();
  }
}
