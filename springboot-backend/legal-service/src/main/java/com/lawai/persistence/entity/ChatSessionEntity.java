package com.lawai.persistence.entity;

import com.lawai.api.model.ChatSessionRecord;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_sessions")
public class ChatSessionEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(nullable = false)
  private String title;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("createdAt ASC")
  private List<ChatMessageEntity> messages = new ArrayList<>();

  protected ChatSessionEntity() {
  }

  public ChatSessionEntity(String id, String userId, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    this.id = id;
    this.userId = userId;
    this.title = title;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static ChatSessionEntity fromRecord(ChatSessionRecord record) {
    ChatSessionEntity entity = new ChatSessionEntity(
        record.id(), record.userId(), record.title(), record.createdAt(), record.updatedAt()
    );
    if (record.messages() != null) {
      for (var message : record.messages()) {
        entity.messages.add(ChatMessageEntity.fromRecord(message, entity));
      }
    }
    return entity;
  }

  public ChatSessionRecord toRecord() {
    List<com.lawai.api.model.ChatMessageRecord> messageRecords = messages.stream()
        .map(ChatMessageEntity::toRecord)
        .toList();
    return new ChatSessionRecord(id, userId, title, createdAt, updatedAt, messageRecords);
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public List<ChatMessageEntity> getMessages() {
    return messages;
  }

  public void addMessage(ChatMessageEntity message) {
    messages.add(message);
  }
}
