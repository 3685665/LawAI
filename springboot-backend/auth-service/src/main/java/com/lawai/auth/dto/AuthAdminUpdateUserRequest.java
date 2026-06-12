package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthAdminUpdateUserRequest(
    @NotBlank(message = "{validation.name.required}") String name,
    @Email(message = "{validation.email.invalid}") @NotBlank(message = "{validation.email.required}") String email,
    @NotBlank(message = "{validation.role.required}")
    @Pattern(regexp = "USER|ADMIN", message = "{validation.role.invalid}") String role,
    Boolean verified,
    @Size(min = 10, message = "{validation.password.size.min}") String password
) {
  public AuthAdminUpdateUserRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim();
    role = role == null ? null : role.trim().toUpperCase();
    password = password == null || password.isBlank() ? null : password;
  }
}
