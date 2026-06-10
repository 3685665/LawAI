package com.lawai.api.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PrecedentTextSupport {

  private static final Pattern OUTCOME_PATTERN = Pattern.compile(
      "(?i)(?:SONU[CÇ]|HÜKÜM|HUKUM|KARAR\\s+SONUCU|KARAR)\\s*[:\\-–]?\\s*(.{20,500})"
  );
  private static final Pattern HEADER_PATTERN = Pattern.compile(
      "(?i).*(?:mahkemesi|dairesi|daire|kurul).*\\d{4}/\\d+.*"
  );

  private PrecedentTextSupport() {
  }

  static String extractOutcome(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    String normalized = content.replaceAll("\\s+", " ").trim();
    Matcher matcher = OUTCOME_PATTERN.matcher(normalized);
    String best = "";
    while (matcher.find()) {
      String candidate = cleanSnippet(matcher.group(1));
      if (candidate.length() > best.length()) {
        best = candidate;
      }
    }
    return truncate(best, 320);
  }

  static String extractSubject(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    String[] lines = content.split("\\R");
    StringBuilder builder = new StringBuilder();
    for (String rawLine : lines) {
      String line = rawLine == null ? "" : rawLine.trim();
      if (line.isBlank() || HEADER_PATTERN.matcher(line).matches()) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(line);
      if (builder.length() >= 1800) {
        break;
      }
      if (OUTCOME_PATTERN.matcher(line).find()) {
        break;
      }
    }
    return cleanSnippet(builder.toString());
  }

  private static String cleanSnippet(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.replaceAll("\\s{2,}", " ").trim();
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return "";
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength).trim() + "...";
  }
}
