package com.lawai.auth.dto;

import java.time.OffsetDateTime;

public record AuthRegisterResponse(
    String message,
    String previewToken,
    OffsetDateTime expiresAt,
    String previewLink
) {
}
