package com.lawai.api.service;

import com.lawai.api.dto.FeedbackCreateRequest;
import com.lawai.api.dto.FeedbackRecordDto;
import com.lawai.api.dto.FeedbackStatusUpdateRequest;
import com.lawai.api.dto.FeedbackUpdateRequest;
import com.lawai.api.model.FeedbackRecord;
import com.lawai.common.i18n.I18nMessages;
import com.lawai.common.model.AuthenticatedUser;
import com.lawai.persistence.entity.FeedbackEntity;
import com.lawai.persistence.repository.FeedbackRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FeedbackService {

  private final FeedbackRepository feedbackRepository;
  private final I18nMessages i18n;

  public FeedbackService(FeedbackRepository feedbackRepository, I18nMessages i18n) {
    this.feedbackRepository = feedbackRepository;
    this.i18n = i18n;
  }

  @Transactional
  public FeedbackRecordDto submit(AuthenticatedUser user, FeedbackCreateRequest request) {
    String type = normalize(request.type());
    if (!StringUtils.hasText(type)) {
      throw new IllegalArgumentException("feedback.type-required");
    }
    String subject = request.subject().trim();
    String message = request.message().trim();
    if (!StringUtils.hasText(subject)) {
      throw new IllegalArgumentException("feedback.subject-required");
    }
    if (!StringUtils.hasText(message)) {
      throw new IllegalArgumentException("feedback.message-required");
    }

    FeedbackEntity entity = FeedbackEntity.fromRecord(new FeedbackRecord(
        UUID.randomUUID().toString(),
        user.id(),
        user.name(),
        user.email(),
        type,
        subject,
        message,
        "received",
        OffsetDateTime.now(ZoneOffset.UTC)
    ));
    return toDto(feedbackRepository.save(entity).toRecord());
  }

  @Transactional(readOnly = true)
  public List<FeedbackRecordDto> listForUser(AuthenticatedUser user) {
    return feedbackRepository.findByUserIdOrderByCreatedAtDesc(user.id()).stream()
        .map(FeedbackEntity::toRecord)
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FeedbackRecordDto> listVisible(AuthenticatedUser user) {
    if (user.isAdmin()) {
      return feedbackRepository.findAll().stream()
          .sorted(Comparator.comparing(FeedbackEntity::getCreatedAt).reversed())
          .map(FeedbackEntity::toRecord)
          .map(this::toDto)
          .toList();
    }
    return listForUser(user);
  }

  @Transactional
  public FeedbackRecordDto updateStatus(AuthenticatedUser user, String feedbackId, FeedbackStatusUpdateRequest request) {
    if (!user.isAdmin()) {
      throw new AccessDeniedException(i18n.get("error.admin-required"));
    }
    String status = normalizeStatus(request.status());
    if (!StringUtils.hasText(status)) {
      throw new IllegalArgumentException("feedback.status-required");
    }
    FeedbackEntity entity = feedbackRepository.findById(feedbackId)
        .orElseThrow(() -> new IllegalArgumentException("feedback.not-found"));
    entity.setStatus(status);
    return toDto(feedbackRepository.save(entity).toRecord());
  }

  @Transactional
  public FeedbackRecordDto update(AuthenticatedUser user, String feedbackId, FeedbackUpdateRequest request) {
    if (!user.isAdmin()) {
      throw new AccessDeniedException(i18n.get("error.admin-required"));
    }
    String type = normalize(request.type());
    String subject = request.subject().trim();
    String message = request.message().trim();
    String status = normalizeStatus(request.status());
    if (!StringUtils.hasText(type)) {
      throw new IllegalArgumentException("feedback.type-required");
    }
    if (!StringUtils.hasText(subject)) {
      throw new IllegalArgumentException("feedback.subject-required");
    }
    if (!StringUtils.hasText(message)) {
      throw new IllegalArgumentException("feedback.message-required");
    }
    if (!StringUtils.hasText(status)) {
      throw new IllegalArgumentException("feedback.status-required");
    }

    FeedbackEntity entity = feedbackRepository.findById(feedbackId)
        .orElseThrow(() -> new IllegalArgumentException("feedback.not-found"));
    entity.update(type, subject, message, status);
    return toDto(feedbackRepository.save(entity).toRecord());
  }

  @Transactional
  public void delete(AuthenticatedUser user, String feedbackId) {
    if (!user.isAdmin()) {
      throw new AccessDeniedException(i18n.get("error.admin-required"));
    }
    if (!feedbackRepository.existsById(feedbackId)) {
      throw new IllegalArgumentException("feedback.not-found");
    }
    feedbackRepository.deleteById(feedbackId);
  }

  private FeedbackRecordDto toDto(FeedbackRecord record) {
    return new FeedbackRecordDto(record.id(), record.userName(), record.userEmail(), record.type(), record.subject(), record.message(), record.status(), record.createdAt());
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
