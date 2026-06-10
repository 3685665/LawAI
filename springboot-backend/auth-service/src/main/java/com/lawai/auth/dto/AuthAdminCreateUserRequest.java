package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthAdminCreateUserRequest(
    @NotBlank(message = "Ad soyad gerekli.") String name,
    @Email(message = "Gecerli bir e-posta adresi girin.") @NotBlank(message = "E-posta gerekli.") String email,
    @NotBlank(message = "Sifre gerekli.") @Size(min = 10, message = "Sifre en az 10 karakter olmali.") String password,
    @NotBlank(message = "Rol gerekli.")
    @Pattern(regexp = "USER|ADMIN", message = "Rol USER veya ADMIN olmali.") String role
) {
  public AuthAdminCreateUserRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim();
    role = role == null ? null : role.trim().toUpperCase();
  }
}
