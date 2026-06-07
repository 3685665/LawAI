package com.lawai.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.ChatMessageDto;
import com.lawai.api.dto.ChatResponse;
import com.lawai.api.dto.ChatSessionDto;
import com.lawai.api.model.ChatMessageRecord;
import com.lawai.api.model.ChatSessionRecord;
import com.lawai.api.model.ChatStorePayload;
import com.lawai.auth.model.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ChatHistoryService {

  private final ObjectMapper objectMapper;
  private final Path storagePath;

  public ChatHistoryService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.storagePath = Path.of(System.getProperty("user.dir"), "data", "chat-store.json");
  }

  public ChatSessionDto saveExchange(AuthenticatedUser user, String sessionId, String userText, ChatResponse response) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<ChatSessionRecord> sessions = new ArrayList<>(load());
    ChatSessionRecord session = findOwnedSession(sessions, user, sessionId);
    List<ChatMessageRecord> messages = session == null || session.messages() == null
        ? new ArrayList<>()
        : new ArrayList<>(session.messages());

    messages.add(new ChatMessageRecord(UUID.randomUUID().toString(), "user", clean(userText, ""), List.of(), null, now));
    messages.add(new ChatMessageRecord(
        UUID.randomUUID().toString(),
        "assistant",
        clean(response.answer(), ""),
        response.citations() == null ? List.of() : response.citations(),
        response.disclaimer(),
        now
    ));

    ChatSessionRecord updated = new ChatSessionRecord(
        session == null ? UUID.randomUUID().toString() : session.id(),
        user.id(),
        session == null ? titleFrom(userText) : session.title(),
        session == null ? now : session.createdAt(),
        now,
        messages
    );

    if (session != null) {
      sessions.removeIf(item -> item.id().equals(session.id()));
    }
    sessions.add(updated);
    save(sessions);
    return toDto(updated);
  }

  public List<ChatSessionDto> listForUser(AuthenticatedUser user) {
    return load().stream()
        .filter(item -> item.userId().equals(user.id()))
        .sorted(Comparator.comparing(ChatSessionRecord::updatedAt).reversed())
        .map(this::toSummaryDto)
        .toList();
  }

  public ChatSessionDto getForUser(AuthenticatedUser user, String sessionId) {
    ChatSessionRecord session = load().stream()
        .filter(item -> item.userId().equals(user.id()) && item.id().equals(sessionId))
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException("Sohbet gecmisi bulunamadi."));
    return toDto(session);
  }

  public List<ChatSessionDto> deleteForUser(AuthenticatedUser user, String sessionId) {
    List<ChatSessionRecord> sessions = new ArrayList<>(load());
    boolean removed = sessions.removeIf(item -> item.userId().equals(user.id()) && item.id().equals(sessionId));
    if (!removed) {
      throw new AccessDeniedException("Sohbet gecmisi bulunamadi.");
    }
    save(sessions);
    return listForUser(user);
  }

  private ChatSessionRecord findOwnedSession(List<ChatSessionRecord> sessions, AuthenticatedUser user, String sessionId) {
    if (!StringUtils.hasText(sessionId)) {
      return null;
    }
    return sessions.stream()
        .filter(item -> item.userId().equals(user.id()) && item.id().equals(sessionId))
        .findFirst()
        .orElse(null);
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

  private List<ChatSessionRecord> load() {
    if (!Files.exists(storagePath)) {
      return new ArrayList<>();
    }
    try {
      ChatStorePayload payload = objectMapper.readValue(Files.readString(storagePath), ChatStorePayload.class);
      return payload.sessions() == null ? new ArrayList<>() : new ArrayList<>(payload.sessions());
    } catch (IOException exception) {
      throw new IllegalStateException("Sohbet gecmisi yuklenemedi: " + exception.getMessage(), exception);
    }
  }

  private void save(List<ChatSessionRecord> sessions) {
    try {
      Files.createDirectories(storagePath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), new ChatStorePayload(sessions));
    } catch (IOException exception) {
      throw new IllegalStateException("Sohbet gecmisi kaydedilemedi: " + exception.getMessage(), exception);
    }
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
