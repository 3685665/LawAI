package com.lawai.auth.dto;

import java.time.OffsetDateTime;

public record AuthPasswordResetResponse(
    String message,
    String resetTokenPreview,
    OffsetDateTime expiresAt,
    String resetLinkPreview
) {
}
