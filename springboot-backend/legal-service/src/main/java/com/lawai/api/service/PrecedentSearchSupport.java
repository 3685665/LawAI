package com.lawai.api.service;

import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PrecedentSearchSupport {

  private static final Pattern DOCKET_PATTERN = Pattern.compile("^(\\d{4})\\s*/\\s*(\\d+)$");
  private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
      DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT),
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT),
      DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ROOT),
      DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT),
      DateTimeFormatter.ISO_LOCAL_DATE
  );

  private PrecedentSearchSupport() {
  }

  static boolean isAdvanced(PrecedentSearchRequest request) {
    return hasText(request.chamber())
        || hasText(request.docketNo())
        || hasText(request.decisionNo())
        || hasText(request.dateFrom())
        || hasText(request.dateTo());
  }

  static boolean hasCriteria(PrecedentSearchRequest request) {
    return hasText(request.query()) || isAdvanced(request);
  }

  static String normalizeOptionalQuery(String query, boolean advanced) {
    String normalized = query == null ? "" : query.trim();
    if (!normalized.isBlank()) {
      if (normalized.length() < 3) {
        throw new IllegalArgumentException("Ictihat aramasi icin en az 3 karakter girin.");
      }
      return normalized;
    }
    if (advanced) {
      return "";
    }
    throw new IllegalArgumentException("Arama yapmak icin sorgu veya gelismis filtre girin.");
  }

  static String[] parseDocketParts(String value) {
    if (!hasText(value)) {
      return new String[] { "", "" };
    }
    Matcher matcher = DOCKET_PATTERN.matcher(value.trim());
    if (!matcher.matches()) {
      return new String[] { "", value.trim() };
    }
    return new String[] { matcher.group(1), matcher.group(2) };
  }

  static String normalizeDate(String value) {
    if (!hasText(value)) {
      return "";
    }
    String trimmed = value.trim();
    for (DateTimeFormatter formatter : DATE_FORMATTERS) {
      try {
        LocalDate date = LocalDate.parse(trimmed, formatter);
        return date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT));
      } catch (DateTimeParseException ignored) {
        // try next format
      }
    }
    return trimmed;
  }

  static List<PrecedentDto> applyAdvancedFilters(PrecedentSearchRequest request, List<PrecedentDto> results) {
    if (!isAdvanced(request)) {
      return results;
    }
    LocalDate from = parseDate(request.dateFrom());
    LocalDate to = parseDate(request.dateTo());
    String chamber = normalize(request.chamber()).toLowerCase(Locale.ROOT);
    String docket = normalize(request.docketNo());
    String decision = normalize(request.decisionNo());

    return results.stream()
        .filter((item) -> matchesChamber(item, chamber))
        .filter((item) -> matchesDocket(item.docketNo(), docket))
        .filter((item) -> matchesDocket(item.decisionNo(), decision))
        .filter((item) -> matchesDateRange(item.date(), from, to))
        .toList();
  }

  private static boolean matchesChamber(PrecedentDto item, String chamber) {
    if (!hasText(chamber)) {
      return true;
    }
    String value = item.chamber() == null ? "" : item.chamber().toLowerCase(Locale.ROOT);
    return value.contains(chamber) || chamber.contains(value);
  }

  private static boolean matchesDocket(String actual, String expected) {
    if (!hasText(expected)) {
      return true;
    }
    String normalizedActual = normalize(actual).replace(" ", "");
    String normalizedExpected = normalize(expected).replace(" ", "");
    return normalizedActual.contains(normalizedExpected) || normalizedExpected.contains(normalizedActual);
  }

  private static boolean matchesDateRange(String dateValue, LocalDate from, LocalDate to) {
    if (from == null && to == null) {
      return true;
    }
    LocalDate date = parseDate(dateValue);
    if (date == null) {
      return from == null && to == null;
    }
    if (from != null && date.isBefore(from)) {
      return false;
    }
    return to == null || !date.isAfter(to);
  }

  private static LocalDate parseDate(String value) {
    if (!hasText(value)) {
      return null;
    }
    String normalized = normalizeDate(value);
    for (DateTimeFormatter formatter : DATE_FORMATTERS) {
      try {
        return LocalDate.parse(normalized, formatter);
      } catch (DateTimeParseException ignored) {
        // try next format
      }
    }
    return null;
  }

  private static boolean hasText(String value) {
    return StringUtils.hasText(value);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
