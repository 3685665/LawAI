package com.lawai.auth.model;

import java.time.OffsetDateTime;

public record PasswordResetRecord(
    String tokenHash,
    String userId,
    OffsetDateTime expiresAt,
    boolean used,
    OffsetDateTime createdAt
) {
  public PasswordResetRecord withUsed(boolean value) {
    return new PasswordResetRecord(tokenHash, userId, expiresAt, value, createdAt);
  }
}
