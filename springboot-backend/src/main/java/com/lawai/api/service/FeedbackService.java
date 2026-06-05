package com.lawai.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.FeedbackCreateRequest;
import com.lawai.api.dto.FeedbackRecordDto;
import com.lawai.api.dto.FeedbackUpdateRequest;
import com.lawai.api.dto.FeedbackStatusUpdateRequest;
import com.lawai.api.model.FeedbackRecord;
import com.lawai.api.model.FeedbackStorePayload;
import com.lawai.auth.model.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FeedbackService {

  private final ObjectMapper objectMapper;
  private final Path storagePath;

  public FeedbackService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.storagePath = Path.of(System.getProperty("user.dir"), "data", "feedback-store.json");
  }

  public FeedbackRecordDto submit(AuthenticatedUser user, FeedbackCreateRequest request) {
    String type = normalize(request.type());
    if (!StringUtils.hasText(type)) {
      throw new IllegalArgumentException("Geri bildirim tipi gerekli.");
    }
    String subject = request.subject().trim();
    String message = request.message().trim();
    if (!StringUtils.hasText(subject)) {
      throw new IllegalArgumentException("Baslik gerekli.");
    }
    if (!StringUtils.hasText(message)) {
      throw new IllegalArgumentException("Mesaj gerekli.");
    }

    FeedbackRecord record = new FeedbackRecord(
        UUID.randomUUID().toString(),
        user.id(),
        user.name(),
        user.email(),
        type,
        subject,
        message,
        "received",
        OffsetDateTime.now(ZoneOffset.UTC)
    );
    List<FeedbackRecord> items = new ArrayList<>(load());
    items.add(record);
    save(items);
    return toDto(record);
  }

  public List<FeedbackRecordDto> listForUser(AuthenticatedUser user) {
    return load().stream()
        .filter(item -> item.userId().equals(user.id()))
        .sorted(Comparator.comparing(FeedbackRecord::createdAt).reversed())
        .map(this::toDto)
        .toList();
  }

  public List<FeedbackRecordDto> listVisible(AuthenticatedUser user) {
    if (user.isAdmin()) {
      return load().stream()
          .sorted(Comparator.comparing(FeedbackRecord::createdAt).reversed())
          .map(this::toDto)
          .toList();
    }
    return listForUser(user);
  }

  public FeedbackRecordDto updateStatus(AuthenticatedUser user, String feedbackId, FeedbackStatusUpdateRequest request) {
    if (!user.isAdmin()) {
      throw new AccessDeniedException("Yalnizca yonetici işlem yapabilir.");
    }
    String status = normalizeStatus(request.status());
    if (!StringUtils.hasText(status)) {
      throw new IllegalArgumentException("Durum gerekli.");
    }

    List<FeedbackRecord> items = new ArrayList<>(load());
    boolean updated = false;
    List<FeedbackRecord> rewritten = new ArrayList<>(items.size());
    for (FeedbackRecord item : items) {
      if (item.id().equals(feedbackId)) {
        rewritten.add(new FeedbackRecord(
            item.id(),
            item.userId(),
            item.userName(),
            item.userEmail(),
            item.type(),
            item.subject(),
            item.message(),
            status,
            item.createdAt()
        ));
        updated = true;
      } else {
        rewritten.add(item);
      }
    }

    if (!updated) {
      throw new IllegalArgumentException("Geri bildirim bulunamadi.");
    }

    save(rewritten);
    return rewritten.stream()
        .filter(item -> item.id().equals(feedbackId))
        .findFirst()
        .map(this::toDto)
        .orElseThrow(() -> new IllegalStateException("Geri bildirim guncellenemedi."));
  }

  public FeedbackRecordDto update(AuthenticatedUser user, String feedbackId, FeedbackUpdateRequest request) {
    if (!user.isAdmin()) {
      throw new AccessDeniedException("Yalnizca yonetici işlem yapabilir.");
    }
    String type = normalize(request.type());
    String subject = request.subject().trim();
    String message = request.message().trim();
    String status = normalizeStatus(request.status());
    if (!StringUtils.hasText(type)) {
      throw new IllegalArgumentException("Geri bildirim tipi gerekli.");
    }
    if (!StringUtils.hasText(subject)) {
      throw new IllegalArgumentException("Baslik gerekli.");
    }
    if (!StringUtils.hasText(message)) {
      throw new IllegalArgumentException("Mesaj gerekli.");
    }
    if (!StringUtils.hasText(status)) {
      throw new IllegalArgumentException("Durum gerekli.");
    }

    List<FeedbackRecord> items = new ArrayList<>(load());
    boolean updated = false;
    List<FeedbackRecord> rewritten = new ArrayList<>(items.size());
    for (FeedbackRecord item : items) {
      if (item.id().equals(feedbackId)) {
        rewritten.add(new FeedbackRecord(
            item.id(),
            item.userId(),
            item.userName(),
            item.userEmail(),
            type,
            subject,
            message,
            status,
            item.createdAt()
        ));
        updated = true;
      } else {
        rewritten.add(item);
      }
    }

    if (!updated) {
      throw new IllegalArgumentException("Geri bildirim bulunamadi.");
    }

    save(rewritten);
    return rewritten.stream()
        .filter(item -> item.id().equals(feedbackId))
        .findFirst()
        .map(this::toDto)
        .orElseThrow(() -> new IllegalStateException("Geri bildirim guncellenemedi."));
  }

  public void delete(AuthenticatedUser user, String feedbackId) {
    if (!user.isAdmin()) {
      throw new AccessDeniedException("Yalnizca yonetici işlem yapabilir.");
    }
    List<FeedbackRecord> items = new ArrayList<>(load());
    List<FeedbackRecord> rewritten = items.stream()
        .filter(item -> !item.id().equals(feedbackId))
        .toList();
    if (rewritten.size() == items.size()) {
      throw new IllegalArgumentException("Geri bildirim bulunamadi.");
    }
    save(rewritten);
  }

  private FeedbackRecordDto toDto(FeedbackRecord record) {
    return new FeedbackRecordDto(record.id(), record.userName(), record.userEmail(), record.type(), record.subject(), record.message(), record.status(), record.createdAt());
  }

  private List<FeedbackRecord> load() {
    if (!Files.exists(storagePath)) {
      return new ArrayList<>();
    }
    try {
      FeedbackStorePayload payload = objectMapper.readValue(Files.readString(storagePath), FeedbackStorePayload.class);
      return payload.feedbackItems() == null ? new ArrayList<>() : new ArrayList<>(payload.feedbackItems());
    } catch (IOException exception) {
      throw new IllegalStateException("Geri bildirim verisi yuklenemedi: " + exception.getMessage(), exception);
    }
  }

  private void save(List<FeedbackRecord> items) {
    try {
      Files.createDirectories(storagePath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), new FeedbackStorePayload(items));
    } catch (IOException exception) {
      throw new IllegalStateException("Geri bildirim verisi kaydedilemedi: " + exception.getMessage(), exception);
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private String normalizeStatus(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "received", "read", "resolved" -> normalized;
      default -> "";
    };
  }
}
