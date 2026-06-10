package com.lawai.auth.model;

import java.time.OffsetDateTime;

public record UserRecord(
    String id,
    String name,
    String email,
    String passwordHash,
    String role,
    OffsetDateTime createdAt,
    OffsetDateTime lastLoginAt,
    Boolean verified,
    OffsetDateTime verifiedAt
) {
  public UserRecord withLastLoginAt(OffsetDateTime value) {
    return new UserRecord(id, name, email, passwordHash, role, createdAt, value, verified, verifiedAt);
  }

  public UserRecord withPasswordHash(String value) {
    return new UserRecord(id, name, email, value, role, createdAt, lastLoginAt, verified, verifiedAt);
  }

  public UserRecord withRole(String value) {
    return new UserRecord(id, name, email, passwordHash, value, createdAt, lastLoginAt, verified, verifiedAt);
  }

  public UserRecord withProfile(String name, String email) {
    return new UserRecord(id, name, email, passwordHash, role, createdAt, lastLoginAt, verified, verifiedAt);
  }

  public UserRecord withVerified(Boolean value, OffsetDateTime at) {
    return new UserRecord(id, name, email, passwordHash, role, createdAt, lastLoginAt, value, at);
  }
}
