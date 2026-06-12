package com.lawai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthProfileUpdateRequest(
    @NotBlank(message = "{validation.name.required}")
    @Size(max = 120, message = "{validation.name.size.max}")
    String name,

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 180, message = "{validation.email.size.max}")
    String email
) {
}
