package com.lawai.persistence.entity;

import com.lawai.auth.model.UserRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false, length = 20)
  private String role;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_login_at")
  private OffsetDateTime lastLoginAt;

  private Boolean verified;

  @Column(name = "verified_at")
  private OffsetDateTime verifiedAt;

  protected UserEntity() {
  }

  public UserEntity(String id, String name, String email, String passwordHash, String role,
      OffsetDateTime createdAt, OffsetDateTime lastLoginAt, Boolean verified, OffsetDateTime verifiedAt) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = createdAt;
    this.lastLoginAt = lastLoginAt;
    this.verified = verified;
    this.verifiedAt = verifiedAt;
  }

  public static UserEntity fromRecord(UserRecord record) {
    return new UserEntity(
        record.id(),
        record.name(),
        record.email(),
        record.passwordHash(),
        record.role(),
        record.createdAt(),
        record.lastLoginAt(),
        record.verified(),
        record.verifiedAt()
    );
  }

  public UserRecord toRecord() {
    return new UserRecord(id, name, email, passwordHash, role, createdAt, lastLoginAt, verified, verifiedAt);
  }

  public void applyRecord(UserRecord record) {
    name = record.name();
    email = record.email();
    passwordHash = record.passwordHash();
    role = record.role();
    lastLoginAt = record.lastLoginAt();
    verified = record.verified();
    verifiedAt = record.verifiedAt();
  }

  public String getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }
}
