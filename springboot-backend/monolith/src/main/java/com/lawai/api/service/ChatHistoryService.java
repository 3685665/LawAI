package com.lawai.api.service;

import com.lawai.api.dto.ChatMessageDto;
import com.lawai.api.dto.ChatResponse;
import com.lawai.api.dto.ChatSessionDto;
import com.lawai.api.model.ChatMessageRecord;
import com.lawai.api.model.ChatSessionRecord;
import com.lawai.auth.model.AuthenticatedUser;
import com.lawai.persistence.entity.ChatMessageEntity;
import com.lawai.persistence.entity.ChatSessionEntity;
import com.lawai.persistence.repository.ChatSessionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ChatHistoryService {

  private final ChatSessionRepository chatSessionRepository;

  public ChatHistoryService(ChatSessionRepository chatSessionRepository) {
    this.chatSessionRepository = chatSessionRepository;
  }

  @Transactional
  public ChatSessionDto saveExchange(AuthenticatedUser user, String sessionId, String userText, ChatResponse response) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ChatSessionEntity session = findOwnedSession(user, sessionId);
    if (session == null) {
      session = new ChatSessionEntity(UUID.randomUUID().toString(), user.id(), titleFrom(userText), now, now);
    } else {
      session.setUpdatedAt(now);
    }

    session.addMessage(new ChatMessageEntity(
        UUID.randomUUID().toString(), session, "user", clean(userText, ""), List.of(), null, now
    ));
    session.addMessage(new ChatMessageEntity(
        UUID.randomUUID().toString(),
        session,
        "assistant",
        clean(response.answer(), ""),
        response.citations() == null ? List.of() : response.citations(),
        response.disclaimer(),
        now
    ));

    return toDto(chatSessionRepository.save(session).toRecord());
  }

  @Transactional(readOnly = true)
  public List<ChatSessionDto> listForUser(AuthenticatedUser user) {
    return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(user.id()).stream()
        .map(ChatSessionEntity::toRecord)
        .map(this::toSummaryDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public ChatSessionDto getForUser(AuthenticatedUser user, String sessionId) {
    ChatSessionEntity session = chatSessionRepository.findByIdAndUserId(sessionId, user.id())
        .orElseThrow(() -> new AccessDeniedException("Sohbet gecmisi bulunamadi."));
    return toDto(session.toRecord());
  }

  @Transactional
  public List<ChatSessionDto> deleteForUser(AuthenticatedUser user, String sessionId) {
    ChatSessionEntity session = chatSessionRepository.findByIdAndUserId(sessionId, user.id())
        .orElseThrow(() -> new AccessDeniedException("Sohbet gecmisi bulunamadi."));
    chatSessionRepository.delete(session);
    return listForUser(user);
  }

  private ChatSessionEntity findOwnedSession(AuthenticatedUser user, String sessionId) {
    if (!StringUtils.hasText(sessionId)) {
      return null;
    }
    return chatSessionRepository.findByIdAndUserId(sessionId, user.id()).orElse(null);
  }

  private ChatSessionDto toSummaryDto(ChatSessionRecord record) {
    return new ChatSessionDto(record.id(), record.title(), record.createdAt(), record.updatedAt(), List.of());
  }

  private ChatSessionDto toDto(ChatSessionRecord record) {
    List<ChatMessageDto> messages = record.messages() == null
        ? List.of()
        : record.messages().stream().map(this::toMessageDto).toList();
    return new ChatSessionDto(record.id(), record.title(), record.createdAt(), record.updatedAt(), messages);
  }

  private ChatMessageDto toMessageDto(ChatMessageRecord record) {
    return new ChatMessageDto(
        record.id(),
        record.role(),
        record.text(),
        record.citations() == null ? List.of() : record.citations(),
        record.disclaimer(),
        record.createdAt()
    );
  }

  private String titleFrom(String value) {
    String cleaned = clean(value, "Yeni sohbet").replaceAll("\\s+", " ");
    return truncate(cleaned, 56);
  }

  private String clean(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
  }
}
