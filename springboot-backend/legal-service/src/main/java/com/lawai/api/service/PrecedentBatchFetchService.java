package com.lawai.api.service;

import com.lawai.api.dto.PrecedentBatchContentResponse;
import com.lawai.api.dto.PrecedentBatchItemDto;
import com.lawai.api.dto.PrecedentBatchPageRequest;
import com.lawai.api.dto.PrecedentBatchPageResponse;
import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class PrecedentBatchFetchService {

  private static final int BATCH_PAGE_SIZE = 50;

  private final YargitayPrecedentService yargitayPrecedentService;
  private final DanistayPrecedentService danistayPrecedentService;
  private final AnayasaPrecedentService anayasaPrecedentService;

  public PrecedentBatchFetchService(
      YargitayPrecedentService yargitayPrecedentService,
      DanistayPrecedentService danistayPrecedentService,
      AnayasaPrecedentService anayasaPrecedentService
  ) {
    this.yargitayPrecedentService = yargitayPrecedentService;
    this.danistayPrecedentService = danistayPrecedentService;
    this.anayasaPrecedentService = anayasaPrecedentService;
  }

  public PrecedentBatchPageResponse fetchPage(PrecedentBatchPageRequest request) {
    int pageSize = normalizePageSize(request.pageSize());
    PrecedentSearchRequest searchRequest = toSearchRequest(request);
    PrecedentBatchPageResult pageResult = switch (normalizeCourt(request.court())) {
      case "YARGITAY" -> yargitayPrecedentService.searchBatchPage(searchRequest, request.page(), pageSize);
      case "DANISTAY" -> danistayPrecedentService.searchBatchPage(searchRequest, request.page(), pageSize);
      case "ANAYASA" -> anayasaPrecedentService.searchBatchPage(searchRequest, request.page(), pageSize);
      default -> throw new IllegalArgumentException("Desteklenmeyen mahkeme: " + request.court());
    };
    List<PrecedentBatchItemDto> items = pageResult.items().stream()
        .map(this::toBatchItem)
        .toList();
    return new PrecedentBatchPageResponse(items, pageResult.hasMore());
  }

  public PrecedentBatchContentResponse fetchContent(String court, String sourceId) {
    PrecedentDto precedent = switch (normalizeCourt(court)) {
      case "YARGITAY" -> yargitayPrecedentService.getDocument(sourceId);
      case "DANISTAY" -> danistayPrecedentService.getDocument(sourceId);
      case "ANAYASA" -> anayasaPrecedentService.getDocument(sourceId);
      default -> throw new IllegalArgumentException("Desteklenmeyen mahkeme: " + court);
    };
    String plainText = extractPlainText(precedent);
    return new PrecedentBatchContentResponse(
        precedent.sourceId(),
        precedent.court(),
        precedent.topic(),
        plainText
    );
  }

  private PrecedentSearchRequest toSearchRequest(PrecedentBatchPageRequest request) {
    return new PrecedentSearchRequest(
        normalizeQuery(request.query()),
        courtLabel(request.court()),
        null,
        null,
        null,
        request.dateFrom(),
        request.dateTo(),
        BATCH_PAGE_SIZE
    );
  }

  private PrecedentBatchItemDto toBatchItem(PrecedentDto precedent) {
    return new PrecedentBatchItemDto(
        precedent.sourceId(),
        precedent.court(),
        precedent.topic(),
        precedent.date()
    );
  }

  private String extractPlainText(PrecedentDto precedent) {
    String content = precedent.content();
    if (!StringUtils.hasText(content)) {
      return precedent.summary() == null ? "" : precedent.summary();
    }
    if (PrecedentHtmlSupport.looksLikeHtml(content)) {
      return PrecedentHtmlSupport.toPlainText(PrecedentHtmlSupport.sanitizeHtml(content));
    }
    return content.trim();
  }

  private String normalizeQuery(String query) {
    return query == null ? "" : query.trim();
  }

  private String normalizeCourt(String court) {
    return court == null ? "" : court.trim().toUpperCase(Locale.ROOT);
  }

  private String courtLabel(String court) {
    return switch (normalizeCourt(court)) {
      case "YARGITAY" -> "Yargitay";
      case "DANISTAY" -> "Danistay";
      case "ANAYASA" -> "AYM";
      default -> court;
    };
  }

  private int normalizePageSize(int pageSize) {
    return Math.min(Math.max(pageSize, 1), 100);
  }
}
