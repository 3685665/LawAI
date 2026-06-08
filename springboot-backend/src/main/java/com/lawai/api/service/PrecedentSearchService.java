package com.lawai.api.service;

import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class PrecedentSearchService {

  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;

  private final YargitayPrecedentService yargitayPrecedentService;
  private final DanistayPrecedentService danistayPrecedentService;
  private final AnayasaPrecedentService anayasaPrecedentService;

  public PrecedentSearchService(
      YargitayPrecedentService yargitayPrecedentService,
      DanistayPrecedentService danistayPrecedentService,
      AnayasaPrecedentService anayasaPrecedentService
  ) {
    this.yargitayPrecedentService = yargitayPrecedentService;
    this.danistayPrecedentService = danistayPrecedentService;
    this.anayasaPrecedentService = anayasaPrecedentService;
  }

  public List<PrecedentDto> search(PrecedentSearchRequest request) {
    int limit = normalizeLimit(request.limit());
    CourtSelection selection = CourtSelection.from(request.court());

    List<PrecedentDto> results = new ArrayList<>();
    if (selection.includesYargitay()) {
      results.addAll(yargitayPrecedentService.search(withLimit(request, limit)));
    }
    if (selection.includesDanistay()) {
      results.addAll(danistayPrecedentService.search(withLimit(request, limit)));
    }
    if (selection.includesAnayasa()) {
      results.addAll(anayasaPrecedentService.search(withLimit(request, limit)));
    }

    return results.stream()
        .sorted(Comparator.comparing(this::sortKey).reversed())
        .limit(limit)
        .toList();
  }

  private PrecedentSearchRequest withLimit(PrecedentSearchRequest request, int limit) {
    return new PrecedentSearchRequest(request.query(), request.court(), request.chamber(), limit);
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private LocalDate sortKey(PrecedentDto precedent) {
    String date = precedent.date();
    if (!StringUtils.hasText(date)) {
      return LocalDate.MIN;
    }
    List<DateTimeFormatter> formatters = List.of(
        DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT),
        DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT)
    );
    for (DateTimeFormatter formatter : formatters) {
      try {
        return LocalDate.parse(date.trim(), formatter);
      } catch (DateTimeParseException ignored) {
        // try next format
      }
    }
    return LocalDate.MIN;
  }

  private enum CourtSelection {
    ALL,
    YARGITAY,
    DANISTAY,
    ANAYASA
    ;

    static CourtSelection from(String court) {
      if (!StringUtils.hasText(court)) {
        return ALL;
      }
      String normalized = court.trim().toLowerCase(Locale.ROOT);
      if (normalized.contains("yarg")) {
        return YARGITAY;
      }
      if (normalized.contains("danis") || normalized.contains("dani")) {
        return DANISTAY;
      }
      if (normalized.contains("aym") || normalized.contains("anayasa")) {
        return ANAYASA;
      }
      return ALL;
    }

    boolean isAll() {
      return this == ALL;
    }

    boolean includesYargitay() {
      return this == ALL || this == YARGITAY;
    }

    boolean includesDanistay() {
      return this == ALL || this == DANISTAY;
    }

    boolean includesAnayasa() {
      return this == ALL || this == ANAYASA;
    }
  }
}
