package com.lawai.document.batch;

import com.lawai.document.batch.dto.BatchDocumentJobRequest;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class BatchJobNameGenerator {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private BatchJobNameGenerator() {
  }

  public static String resolveName(BatchDocumentJobRequest request, Path directory) {
    if (StringUtils.hasText(request.name())) {
      return request.name().trim();
    }
    String folder = directory.getFileName() == null ? "batch" : directory.getFileName().toString();
    String schedule = scheduleLabel(request.scheduleType());
    String time = request.scheduledTime().replace(":", "-");
    String suffix = switch (BatchScheduleType.valueOf(request.scheduleType().trim().toUpperCase(Locale.ROOT))) {
      case ONCE -> request.scheduledDate() == null ? "tek" : request.scheduledDate().format(DATE_FORMAT);
      case WEEKLY -> request.dayOfWeek() == null ? "hafta" : "gun" + request.dayOfWeek();
      case MONTHLY -> request.dayOfMonth() == null ? "ay" : "ay" + request.dayOfMonth();
      default -> "";
    };
    String raw = suffix.isBlank() ? folder + "-" + schedule + "-" + time : folder + "-" + schedule + "-" + suffix + "-" + time;
    return sanitize(raw);
  }

  private static String scheduleLabel(String scheduleType) {
    return switch (BatchScheduleType.valueOf(scheduleType.trim().toUpperCase(Locale.ROOT))) {
      case ONCE -> "tek";
      case DAILY -> "gunluk";
      case WEEKLY -> "haftalik";
      case MONTHLY -> "aylik";
    };
  }

  private static String sanitize(String value) {
    String normalized = value
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9._-]+", "-")
        .replaceAll("-{2,}", "-")
        .replaceAll("^-|-$", "");
    if (normalized.isBlank()) {
      return "batch-job";
    }
    return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
  }
}
