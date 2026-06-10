package com.lawai.persistence.entity;

import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.model.ChatMessageRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private ChatSessionEntity session;

  @Column(nullable = false, length = 20)
  private String role;

  @Column(nullable = false, columnDefinition = "text")
  private String text;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<PrecedentDto> citations = new ArrayList<>();

  @Column(columnDefinition = "text")
  private String disclaimer;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ChatMessageEntity() {
  }

  public ChatMessageEntity(String id, ChatSessionEntity session, String role, String text,
      List<PrecedentDto> citations, String disclaimer, OffsetDateTime createdAt) {
    this.id = id;
    this.session = session;
    this.role = role;
    this.text = text;
    this.citations = citations == null ? new ArrayList<>() : new ArrayList<>(citations);
    this.disclaimer = disclaimer;
    this.createdAt = createdAt;
  }

  public static ChatMessageEntity fromRecord(ChatMessageRecord record, ChatSessionEntity session) {
    return new ChatMessageEntity(
        record.id(), session, record.role(), record.text(), record.citations(), record.disclaimer(), record.createdAt()
    );
  }

  public ChatMessageRecord toRecord() {
    return new ChatMessageRecord(id, role, text, citations == null ? List.of() : citations, disclaimer, createdAt);
  }
}
