package com.lawai.api.service;

import com.lawai.api.dto.ActivityLogCreateRequest;
import com.lawai.api.dto.ActivityLogDto;
import com.lawai.common.i18n.I18nMessages;
import com.lawai.common.model.AuthenticatedUser;
import com.lawai.persistence.entity.ActivityLogEntity;
import com.lawai.persistence.repository.ActivityLogRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ActivityLogService {

  private final ActivityLogRepository activityLogRepository;
  private final I18nMessages i18n;

  public ActivityLogService(ActivityLogRepository activityLogRepository, I18nMessages i18n) {
    this.activityLogRepository = activityLogRepository;
    this.i18n = i18n;
  }

  @Transactional
  public ActivityLogDto logFrontend(AuthenticatedUser user, ActivityLogCreateRequest request) {
    return log(user, "frontend", clean(request.action(), "screen-view"), clean(request.screen(), i18n.get("activity.default-screen")), clean(request.detail(), ""), clean(request.path(), ""));
  }

  @Transactional
  public ActivityLogDto logBackend(AuthenticatedUser user, String action, String screen, String detail, String path) {
    return log(user, "backend", action, screen, detail, path);
  }

  @Transactional(readOnly = true)
  public List<ActivityLogDto> listForUser(AuthenticatedUser user) {
    return activityLogRepository.findByUserIdOrderByCreatedAtDesc(user.id()).stream()
        .map(ActivityLogEntity::toRecord)
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ActivityLogDto> listAll(AuthenticatedUser user) {
    if (!user.isAdmin()) {
      throw new AccessDeniedException(i18n.get("error.admin-required"));
    }
    return activityLogRepository.findAll().stream()
        .sorted(Comparator.comparing(ActivityLogEntity::getCreatedAt).reversed())
        .map(ActivityLogEntity::toRecord)
        .map(this::toDto)
        .toList();
  }

  private ActivityLogDto log(AuthenticatedUser user, String source, String action, String screen, String detail, String path) {
    ActivityLogEntity entity = ActivityLogEntity.fromRecord(new com.lawai.api.model.ActivityLogRecord(
        UUID.randomUUID().toString(),
        user.id(),
        user.name(),
        user.email(),
        user.role(),
        clean(source, "backend"),
        clean(action, "operation"),
        clean(screen, i18n.get("activity.default-screen")),
        truncate(clean(detail, ""), 500),
        truncate(clean(path, ""), 250),
        OffsetDateTime.now(ZoneOffset.UTC)
    ));
    return toDto(activityLogRepository.save(entity).toRecord());
  }

  private ActivityLogDto toDto(com.lawai.api.model.ActivityLogRecord record) {
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

  private String clean(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
