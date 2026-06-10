package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthAdminUpdateUserRequest(
    @NotBlank(message = "Ad soyad gerekli.") String name,
    @Email(message = "Gecerli bir e-posta adresi girin.") @NotBlank(message = "E-posta gerekli.") String email,
    @NotBlank(message = "Rol gerekli.")
    @Pattern(regexp = "USER|ADMIN", message = "Rol USER veya ADMIN olmali.") String role,
    Boolean verified,
    @Size(min = 10, message = "Sifre en az 10 karakter olmali.") String password
) {
  public AuthAdminUpdateUserRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim();
    role = role == null ? null : role.trim().toUpperCase();
    password = password == null || password.isBlank() ? null : password;
  }
}
