package com.lawai.persistence.entity;

import com.lawai.api.model.FeedbackRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "feedback_items")
public class FeedbackEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(name = "user_name", nullable = false)
  private String userName;

  @Column(name = "user_email", nullable = false)
  private String userEmail;

  @Column(nullable = false, length = 50)
  private String type;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false, columnDefinition = "text")
  private String message;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected FeedbackEntity() {
  }

  public FeedbackEntity(String id, String userId, String userName, String userEmail, String type,
      String subject, String message, String status, OffsetDateTime createdAt) {
    this.id = id;
    this.userId = userId;
    this.userName = userName;
    this.userEmail = userEmail;
    this.type = type;
    this.subject = subject;
    this.message = message;
    this.status = status;
    this.createdAt = createdAt;
  }

  public static FeedbackEntity fromRecord(FeedbackRecord record) {
    return new FeedbackEntity(
        record.id(), record.userId(), record.userName(), record.userEmail(),
        record.type(), record.subject(), record.message(), record.status(), record.createdAt()
    );
  }

  public FeedbackRecord toRecord() {
    return new FeedbackRecord(id, userId, userName, userEmail, type, subject, message, status, createdAt);
  }

  public void update(String type, String subject, String message, String status) {
    this.type = type;
    this.subject = subject;
    this.message = message;
    this.status = status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
