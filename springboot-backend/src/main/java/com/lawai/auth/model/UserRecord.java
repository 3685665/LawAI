package com.lawai.auth.model;

import java.time.OffsetDateTime;

public record UserRecord(
    String id,
    String name,
    String email,
    String passwordHash,
    String role,
    OffsetDateTime createdAt,
    OffsetDateTime lastLoginAt
) {
  public UserRecord withLastLoginAt(OffsetDateTime value) {
    return new UserRecord(id, name, email, passwordHash, role, createdAt, value);
  }

  public UserRecord withPasswordHash(String value) {
    return new UserRecord(id, name, email, value, role, createdAt, lastLoginAt);
  }

  public UserRecord withRole(String value) {
    return new UserRecord(id, name, email, passwordHash, value, createdAt, lastLoginAt);
  }
}
