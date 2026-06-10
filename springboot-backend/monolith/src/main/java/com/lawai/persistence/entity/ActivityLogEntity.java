package com.lawai.persistence.entity;

import com.lawai.api.model.ActivityLogRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "activity_logs")
public class ActivityLogEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(name = "user_name", nullable = false)
  private String userName;

  @Column(name = "user_email", nullable = false)
  private String userEmail;

  @Column(nullable = false, length = 20)
  private String role;

  @Column(nullable = false, length = 20)
  private String source;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false)
  private String screen;

  @Column(nullable = false, length = 500)
  private String detail;

  @Column(nullable = false, length = 250)
  private String path;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ActivityLogEntity() {
  }

  public ActivityLogEntity(String id, String userId, String userName, String userEmail, String role,
      String source, String action, String screen, String detail, String path, OffsetDateTime createdAt) {
    this.id = id;
    this.userId = userId;
    this.userName = userName;
    this.userEmail = userEmail;
    this.role = role;
    this.source = source;
    this.action = action;
    this.screen = screen;
    this.detail = detail;
    this.path = path;
    this.createdAt = createdAt;
  }

  public static ActivityLogEntity fromRecord(ActivityLogRecord record) {
    return new ActivityLogEntity(
        record.id(), record.userId(), record.userName(), record.userEmail(), record.role(),
        record.source(), record.action(), record.screen(), record.detail(), record.path(), record.createdAt()
    );
  }

  public ActivityLogRecord toRecord() {
    return new ActivityLogRecord(id, userId, userName, userEmail, role, source, action, screen, detail, path, createdAt);
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
