package com.lawai.persistence.entity;

import com.lawai.auth.model.SessionRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "auth_sessions")
public class AuthSessionEntity {

  @Id
  @Column(name = "token_hash", length = 128)
  private String tokenHash;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AuthSessionEntity() {
  }

  public AuthSessionEntity(String tokenHash, String userId, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
    this.tokenHash = tokenHash;
    this.userId = userId;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public static AuthSessionEntity fromRecord(SessionRecord record) {
    return new AuthSessionEntity(record.tokenHash(), record.userId(), record.expiresAt(), record.createdAt());
  }

  public SessionRecord toRecord() {
    return new SessionRecord(tokenHash, userId, expiresAt, createdAt);
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public String getUserId() {
    return userId;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }
}
