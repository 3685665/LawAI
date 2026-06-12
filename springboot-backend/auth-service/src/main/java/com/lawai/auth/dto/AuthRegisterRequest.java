package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
    @NotBlank(message = "{validation.name.required}") String name,
    @Email(message = "{validation.email.invalid}") @NotBlank(message = "{validation.email.required}") String email,
    @NotBlank(message = "{validation.password.required}") @Size(min = 10, message = "{validation.password.size.min}") String password
) {
  public AuthRegisterRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim();
  }
}
