package com.lawai.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record AuthRegisterResponse(
    String message,
    @JsonProperty("verificationTokenPreview") String previewToken,
    OffsetDateTime expiresAt,
    @JsonProperty("verificationLinkPreview") String previewLink
) {
}
