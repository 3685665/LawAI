package com.lawai.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.ActivityLogCreateRequest;
import com.lawai.api.dto.ActivityLogDto;
import com.lawai.api.model.ActivityLogRecord;
import com.lawai.api.model.ActivityLogStorePayload;
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
public class ActivityLogService {

  private final ObjectMapper objectMapper;
  private final Path storagePath;

  public ActivityLogService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.storagePath = Path.of(System.getProperty("user.dir"), "data", "activity-store.json");
  }

  public ActivityLogDto logFrontend(AuthenticatedUser user, ActivityLogCreateRequest request) {
    return log(user, "frontend", clean(request.action(), "screen-view"), clean(request.screen(), "Uygulama"), clean(request.detail(), ""), clean(request.path(), ""));
  }

  public ActivityLogDto logBackend(AuthenticatedUser user, String action, String screen, String detail, String path) {
    return log(user, "backend", action, screen, detail, path);
  }

  public List<ActivityLogDto> listForUser(AuthenticatedUser user) {
    return load().stream()
        .filter(item -> item.userId().equals(user.id()))
        .sorted(Comparator.comparing(ActivityLogRecord::createdAt).reversed())
        .map(this::toDto)
        .toList();
  }

  public List<ActivityLogDto> listAll(AuthenticatedUser user) {
    if (!user.isAdmin()) {
      throw new AccessDeniedException("Yonetici yetkisi gerekli.");
    }
    return load().stream()
        .sorted(Comparator.comparing(ActivityLogRecord::createdAt).reversed())
        .map(this::toDto)
        .toList();
  }

  private ActivityLogDto log(AuthenticatedUser user, String source, String action, String screen, String detail, String path) {
    ActivityLogRecord record = new ActivityLogRecord(
        UUID.randomUUID().toString(),
        user.id(),
        user.name(),
        user.email(),
        user.role(),
        clean(source, "backend"),
        clean(action, "operation"),
        clean(screen, "Uygulama"),
        truncate(clean(detail, ""), 500),
        truncate(clean(path, ""), 250),
        OffsetDateTime.now(ZoneOffset.UTC)
    );
    List<ActivityLogRecord> logs = new ArrayList<>(load());
    logs.add(record);
    save(logs);
    return toDto(record);
  }

  private ActivityLogDto toDto(ActivityLogRecord record) {
    return new ActivityLogDto(
        record.id(),
        record.userId(),
        record.userName(),
        record.userEmail(),
        record.role(),
        record.source(),
        record.action(),
        record.screen(),
        record.detail(),
        record.path(),
        record.createdAt()
    );
  }

  private List<ActivityLogRecord> load() {
    if (!Files.exists(storagePath)) {
      return new ArrayList<>();
    }
    try {
      ActivityLogStorePayload payload = objectMapper.readValue(Files.readString(storagePath), ActivityLogStorePayload.class);
      return payload.logs() == null ? new ArrayList<>() : new ArrayList<>(payload.logs());
    } catch (IOException exception) {
      throw new IllegalStateException("Islem loglari yuklenemedi: " + exception.getMessage(), exception);
    }
  }

  private void save(List<ActivityLogRecord> logs) {
    try {
      Files.createDirectories(storagePath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), new ActivityLogStorePayload(logs));
    } catch (IOException exception) {
      throw new IllegalStateException("Islem loglari kaydedilemedi: " + exception.getMessage(), exception);
    }
  }

  private String clean(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
