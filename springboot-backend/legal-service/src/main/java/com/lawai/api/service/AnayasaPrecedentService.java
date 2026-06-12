package com.lawai.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AnayasaPrecedentService {

  private static final String BASE_URL = "https://kararlarbilgibankasi.anayasa.gov.tr";
  private static final String SEARCH_URL = BASE_URL + "/api/core/public/search";
  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;
  private static final int MAX_PAGES = 5;

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public AnayasaPrecedentService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  public List<PrecedentDto> search(PrecedentSearchRequest request) {
    boolean advanced = PrecedentSearchSupport.isAdvanced(request);
    String query = resolveQuery(request, advanced);
    int limit = normalizeLimit(request.limit());
    List<PrecedentDto> results = new ArrayList<>();
    try {
      for (int page = 1; page <= MAX_PAGES && results.size() < limit; page++) {
        JsonNode pageResults = searchPage(query, page, limit);
        if (pageResults.isEmpty()) {
          break;
        }
        for (JsonNode row : pageResults) {
          PrecedentDto precedent = toListPrecedent(row);
          if (precedent != null) {
            results.add(precedent);
          }
          if (results.size() >= limit) {
            break;
          }
        }
      }
      return PrecedentSearchSupport.applyAdvancedFilters(request, results.stream()
          .distinct()
          .limit(limit)
          .toList());
    } catch (IOException exception) {
      throw new IllegalStateException("Anayasa Mahkemesi karar arama servisine baglanilamadi: " + exception.getMessage(), exception);
    }
  }

  public PrecedentBatchPageResult searchBatchPage(PrecedentSearchRequest request, int pageNumber, int pageSize) {
    boolean advanced = PrecedentSearchSupport.isAdvanced(request)
        || org.springframework.util.StringUtils.hasText(request.dateFrom())
        || org.springframework.util.StringUtils.hasText(request.dateTo());
    String query = resolveBatchQuery(request, advanced);
    int limit = Math.min(Math.max(pageSize, 1), 100);
    int page = Math.max(pageNumber, 1);
    List<PrecedentDto> results = new ArrayList<>();
    try {
      JsonNode pageResults = searchPage(query, page, limit);
      if (!pageResults.isArray()) {
        return new PrecedentBatchPageResult(List.of(), false);
      }
      for (JsonNode row : pageResults) {
        PrecedentDto precedent = toListPrecedent(row);
        if (precedent != null) {
          results.add(precedent);
        }
      }
      return new PrecedentBatchPageResult(
          PrecedentSearchSupport.applyAdvancedFilters(request, results.stream()
              .distinct()
              .toList()),
          pageResults.size() >= limit
      );
    } catch (IOException exception) {
      throw new IllegalStateException("Anayasa Mahkemesi karar arama servisine baglanilamadi: " + exception.getMessage(), exception);
    }
  }

  public PrecedentDto getDocument(String documentId) {
    String normalizedId = normalizeDocumentId(documentId);
    try {
      JsonNode summary = lookupByLegacyId(normalizedId);
      if (summary == null || summary.isMissingNode()) {
        throw new IllegalStateException("Anayasa Mahkemesi karari bulunamadi: " + normalizedId);
      }
      String kararId = text(summary, "id");
      String kararTipi = text(summary, "kararTipi");
      JsonNode detail = fetchDetail(kararId, kararTipi);
      String rawContentHtml = text(detail, "icerik");
      if (rawContentHtml.isBlank()) {
        rawContentHtml = text(summary, "icerik");
      }
      String contentHtml = PrecedentHtmlSupport.sanitizeHtml(rawContentHtml);
      String plainText = PrecedentHtmlSupport.toPlainText(contentHtml);
      String subject = cleanText(text(detail, "kararKonusu"));
      String summaryText = subject.isBlank() ? preview(plainText, 650) : subject;
      return new PrecedentDto(
          resolveSourceId(detail.isMissingNode() ? summary : detail),
          "Anayasa Mahkemesi",
          text(detail, "kararVerenBirimLabel"),
          text(detail, "basvuruNo"),
          nullableText(detail, "kararNo"),
          formatDate(text(detail, "kararTarihi")),
          cleanText(text(detail, "basvuruAdi")),
          summaryText,
          contentHtml.isBlank() ? plainText : contentHtml,
          nullableText(detail, "kararTuruBasvuruSonucuLabel")
      );
    } catch (IOException exception) {
      throw new IllegalStateException("Anayasa Mahkemesi karar detayi alinamadi: " + exception.getMessage(), exception);
    }
  }

  private JsonNode searchPage(String query, int page, int limit) throws IOException {
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("query", query);
    filter.put("_timestamp", System.currentTimeMillis());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("page", page);
    body.put("size", Math.min(limit, MAX_LIMIT));
    body.put("filter", filter);

    JsonNode response = postSearch(body);
    return response.path("data");
  }

  private JsonNode lookupByLegacyId(String legacyId) throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("page", 0);
    body.put("size", 1);
    body.put("query", legacyId);
    body.put("sort", "yayinTarihi");
    body.put("order", "desc");
    body.put("_timestamp", System.currentTimeMillis());

    JsonNode rows = postSearch(body).path("data");
    if (!rows.isArray() || rows.isEmpty()) {
      return null;
    }
    return rows.get(0);
  }

  private JsonNode fetchDetail(String kararId, String kararTipi) throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", kararId);
    body.put("size", 1);
    body.put("page", 0);
    body.put("_timestamp", System.currentTimeMillis());
    if (kararTipi != null && !kararTipi.isBlank()) {
      body.put("kararTipi", kararTipi);
    }

    JsonNode rows = postSearch(body).path("data");
    if (!rows.isArray() || rows.isEmpty()) {
      return objectMapper.createObjectNode();
    }
    return rows.get(0);
  }

  private JsonNode postSearch(Map<String, Object> body) throws IOException {
    String payload = objectMapper.writeValueAsString(body);
    HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH_URL))
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("User-Agent", "Mozilla/5.0 (compatible; LawAI/1.0)")
        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
        .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new IOException("HTTP " + response.statusCode());
      }
      JsonNode parsed = objectMapper.readTree(response.body());
      if (parsed.has("success") && parsed.path("success").isBoolean() && !parsed.path("success").asBoolean()) {
        throw new IOException(parsed.path("message").asText("Anayasa Mahkemesi arama istegi basarisiz."));
      }
      return parsed;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Karar servisi istegi kesildi.", exception);
    }
  }

  private PrecedentDto toListPrecedent(JsonNode row) {
    if (row == null || row.isMissingNode()) {
      return null;
    }
    String sourceId = resolveSourceId(row);
    if (sourceId.isBlank()) {
      return null;
    }
    String topic = cleanText(text(row, "basvuruAdi"));
    if (topic.isBlank()) {
      topic = cleanText(text(row, "kararAdi"));
    }
    if (topic.isBlank()) {
      return null;
    }
    return new PrecedentDto(
        sourceId,
        "Anayasa Mahkemesi",
        text(row, "kararVerenBirimLabel"),
        text(row, "basvuruNo"),
        nullableText(row, "kararNo"),
        formatDate(text(row, "kararTarihi")),
        topic,
        cleanText(text(row, "kararKonusu")),
        null,
        nullableText(row, "kararTuruBasvuruSonucuLabel")
    );
  }

  private String resolveSourceId(JsonNode row) {
    String basvuruNo = text(row, "basvuruNo");
    if (!basvuruNo.isBlank()) {
      return "BB/" + basvuruNo;
    }
    String esasNo = text(row, "esasNo");
    String kararNo = text(row, "kararNo");
    if (!esasNo.isBlank() && !kararNo.isBlank()) {
      return "ND/" + esasNo + "/" + kararNo;
    }
    String kararTipi = text(row, "kararTipi");
    String id = text(row, "id");
    if (!id.isBlank()) {
      return (kararTipi.isBlank() ? "AYM" : kararTipi) + "/" + id;
    }
    return "";
  }

  private String resolveBatchQuery(PrecedentSearchRequest request, boolean advanced) {
    String query = request.query() == null ? "" : request.query().trim();
    if (!query.isBlank()) {
      return query;
    }
    if (advanced) {
      return "";
    }
    return "anayasa";
  }

  private String resolveQuery(PrecedentSearchRequest request, boolean advanced) {
    String query = PrecedentSearchSupport.normalizeOptionalQuery(request.query(), advanced);
    if (!query.isBlank()) {
      return query;
    }
    if (request.docketNo() != null && !request.docketNo().isBlank()) {
      String docketNo = request.docketNo().trim();
      return docketNo.contains("/") ? "BB/" + docketNo : docketNo;
    }
    if (request.decisionNo() != null && !request.decisionNo().isBlank()) {
      return request.decisionNo().trim();
    }
    if (request.chamber() != null && !request.chamber().isBlank()) {
      return request.chamber().trim();
    }
    return "anayasa";
  }

  private String normalizeDocumentId(String documentId) {
    String normalized = documentId == null ? "" : documentId.trim();
    if (normalized.matches("(BB|ND)/.+")) {
      return normalized;
    }
    if (normalized.matches("\\d{4}/\\d+")) {
      return "BB/" + normalized;
    }
    throw new IllegalArgumentException("Gecersiz Anayasa Mahkemesi karar ID.");
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return "";
    }
    return value.asText("").trim();
  }

  private String nullableText(JsonNode node, String field) {
    String value = text(node, field);
    return value.isBlank() ? null : value;
  }

  private String formatDate(String rawDate) {
    if (rawDate == null || rawDate.isBlank()) {
      return null;
    }
    String normalized = rawDate.trim();
    if (normalized.length() >= 10 && normalized.charAt(4) == '-' && normalized.charAt(7) == '-') {
      try {
        return LocalDate.parse(normalized.substring(0, 10))
            .format(DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT));
      } catch (DateTimeParseException ignored) {
        return normalized;
      }
    }
    return normalized;
  }

  private String cleanText(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String text = value
        .replaceAll("(?is)<script.*?</script>", " ")
        .replaceAll("(?is)<style.*?</style>", " ")
        .replaceAll("(?i)<br\\s*/?>", " ")
        .replaceAll("(?s)<[^>]+>", " ");
    text = HtmlUtils.htmlUnescape(text);
    text = text.replace('\u00a0', ' ');
    text = text.replaceAll("[ \\t\\x0B\\f\\r\\n]+", " ");
    return text.trim();
  }

  private String preview(String content, int maxLength) {
    if (content == null || content.isBlank()) {
      return "";
    }
    if (content.length() <= maxLength) {
      return content;
    }
    return content.substring(0, maxLength) + "...";
  }
}
