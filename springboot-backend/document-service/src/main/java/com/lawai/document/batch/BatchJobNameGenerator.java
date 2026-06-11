package com.lawai.document.batch;

import com.lawai.document.batch.dto.BatchDocumentJobRequest;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

public final class BatchJobNameGenerator {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private BatchJobNameGenerator() {
  }

  public static String resolveName(BatchDocumentJobRequest request, Path directory) {
    if (StringUtils.hasText(request.name())) {
      return request.name().trim();
    }
    BatchSourceType sourceType = parseSourceType(request.sourceType());
    String prefix = sourceType == BatchSourceType.PRECEDENT
        ? precedentPrefix(request)
        : folderLabel(directory);
    String schedule = scheduleLabel(request.scheduleType());
    String time = request.scheduledTime().replace(":", "-");
    String suffix = switch (BatchScheduleType.valueOf(request.scheduleType().trim().toUpperCase(Locale.ROOT))) {
      case ONCE -> request.scheduledDate() == null ? "tek" : request.scheduledDate().format(DATE_FORMAT);
      case WEEKLY -> request.dayOfWeek() == null ? "hafta" : "gun" + request.dayOfWeek();
      case MONTHLY -> request.dayOfMonth() == null ? "ay" : "ay" + request.dayOfMonth();
      default -> "";
    };
    String raw = suffix.isBlank() ? prefix + "-" + schedule + "-" + time : prefix + "-" + schedule + "-" + suffix + "-" + time;
    return sanitize(raw);
  }

  private static String precedentPrefix(BatchDocumentJobRequest request) {
    String courts = request.precedentCourts() == null || request.precedentCourts().isEmpty()
        ? "ictihat"
        : request.precedentCourts().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("-"));
    if (request.precedentDateFrom() != null && request.precedentDateTo() != null) {
      return courts + "-" + request.precedentDateFrom().format(DATE_FORMAT) + "-" + request.precedentDateTo().format(DATE_FORMAT);
    }
    return courts;
  }

  private static String folderLabel(Path directory) {
    if (directory == null || directory.getFileName() == null) {
      return "batch";
    }
    return directory.getFileName().toString();
  }

  private static BatchSourceType parseSourceType(String sourceType) {
    try {
      return BatchSourceType.valueOf(sourceType.trim().toUpperCase(Locale.ROOT));
    } catch (Exception exception) {
      return BatchSourceType.DIRECTORY;
    }
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
