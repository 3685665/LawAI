package com.lawai.auth.model;

import java.time.OffsetDateTime;

public record UserRecord(
    String id,
    String name,
    String email,
    String passwordHash,
    OffsetDateTime createdAt,
    OffsetDateTime lastLoginAt
) {
  public UserRecord withLastLoginAt(OffsetDateTime value) {
    return new UserRecord(id, name, email, passwordHash, createdAt, value);
  }

  public UserRecord withPasswordHash(String value) {
    return new UserRecord(id, name, email, value, createdAt, lastLoginAt);
  }
}
