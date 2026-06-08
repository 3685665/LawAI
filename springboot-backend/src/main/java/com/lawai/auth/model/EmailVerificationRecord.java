package com.lawai.auth.model;

import java.time.OffsetDateTime;

public record EmailVerificationRecord(
    String tokenHash,
    String userId,
    OffsetDateTime expiresAt,
    boolean used,
    OffsetDateTime createdAt
) {
  public EmailVerificationRecord withUsed(boolean value) {
    return new EmailVerificationRecord(tokenHash, userId, expiresAt, value, createdAt);
  }
}
