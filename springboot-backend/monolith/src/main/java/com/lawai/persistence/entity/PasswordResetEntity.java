package com.lawai.persistence.entity;

import com.lawai.auth.model.PasswordResetRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "password_resets")
public class PasswordResetEntity {

  @Id
  @Column(name = "token_hash", length = 128)
  private String tokenHash;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(nullable = false)
  private boolean used;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected PasswordResetEntity() {
  }

  public PasswordResetEntity(String tokenHash, String userId, OffsetDateTime expiresAt, boolean used, OffsetDateTime createdAt) {
    this.tokenHash = tokenHash;
    this.userId = userId;
    this.expiresAt = expiresAt;
    this.used = used;
    this.createdAt = createdAt;
  }

  public static PasswordResetEntity fromRecord(PasswordResetRecord record) {
    return new PasswordResetEntity(record.tokenHash(), record.userId(), record.expiresAt(), record.used(), record.createdAt());
  }

  public PasswordResetRecord toRecord() {
    return new PasswordResetRecord(tokenHash, userId, expiresAt, used, createdAt);
  }

  public void setUsed(boolean used) {
    this.used = used;
  }

  public String getUserId() {
    return userId;
  }

  public boolean isUsed() {
    return used;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public String getTokenHash() {
    return tokenHash;
  }
}
