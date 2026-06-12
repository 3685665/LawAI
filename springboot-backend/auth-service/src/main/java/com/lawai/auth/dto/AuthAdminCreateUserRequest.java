package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthAdminCreateUserRequest(
    @NotBlank(message = "{validation.name.required}") String name,
    @Email(message = "{validation.email.invalid}") @NotBlank(message = "{validation.email.required}") String email,
    @NotBlank(message = "{validation.password.required}") @Size(min = 10, message = "{validation.password.size.min}") String password,
    @NotBlank(message = "{validation.role.required}")
    @Pattern(regexp = "USER|ADMIN", message = "{validation.role.invalid}") String role
) {
  public AuthAdminCreateUserRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim();
    role = role == null ? null : role.trim().toUpperCase();
  }
}
