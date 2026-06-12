package com.lawai.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.KnowledgeDocumentRequest;
import com.lawai.api.dto.KnowledgeIngestRequest;
import com.lawai.api.dto.KnowledgeIngestResponse;
import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import com.lawai.api.dto.PrecedentSyncRequest;
import com.lawai.api.dto.PrecedentSyncResponse;
import com.lawai.api.service.AiServiceClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DanistayPrecedentService {

  private static final String BASE_URL = "https://karararama.danistay.gov.tr";
  private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;
  private static final int SYNC_PAGE_LIMIT = 100;
  private static final int SYNC_MAX_PAGES = 40;
  private static final Pattern HEADER_PATTERN = Pattern.compile("^(.*?)\\s+(\\d{4}/\\d+)\\s*E\\.?\\s*,\\s*(\\d{4}/\\d+)\\s*K\\.?\\b", Pattern.DOTALL);
  private static final Pattern DOCUMENT_PATTERN = Pattern.compile("<p id=\"hiddencontent\" style=\"display: none\">(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  private final ObjectMapper objectMapper;
  private final AiServiceClient aiServiceClient;

  public DanistayPrecedentService(ObjectMapper objectMapper, AiServiceClient aiServiceClient) {
    this.objectMapper = objectMapper;
    this.aiServiceClient = aiServiceClient;
  }

  public List<PrecedentDto> search(PrecedentSearchRequest request) {
    boolean advanced = PrecedentSearchSupport.isAdvanced(request);
    String query = PrecedentSearchSupport.normalizeOptionalQuery(request.query(), advanced);
    int limit = normalizeLimit(request.limit());
    BasicCookieStore cookieStore = new BasicCookieStore();

    try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
      sendGet(client, BASE_URL);
      Map<String, Object> data = advanced ? detailedSearchPayload(request, query) : searchPayload(query);
      sendPost(client, BASE_URL + "/arama", objectMapper.writeValueAsString(Map.of("data", data)));
      data.put("pageSize", String.valueOf(limit));
      data.put("pageNumber", "1");
      String json = sendPost(client, BASE_URL + "/aramalist", objectMapper.writeValueAsString(Map.of("data", data)));
      return PrecedentSearchSupport.applyAdvancedFilters(request, parseResults(json));
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Danistay karar arama servisine baglanilamadi: " + exception.getMessage(), exception);
    }
  }

  public PrecedentBatchPageResult searchBatchPage(PrecedentSearchRequest request, int pageNumber, int pageSize) {
    boolean advanced = PrecedentSearchSupport.isAdvanced(request)
        || StringUtils.hasText(request.dateFrom())
        || StringUtils.hasText(request.dateTo());
    String query = request.query() == null ? "" : request.query().trim();
    int limit = Math.min(Math.max(pageSize, 1), 100);
    int page = Math.max(pageNumber, 1);
    BasicCookieStore cookieStore = new BasicCookieStore();

    try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
      sendGet(client, BASE_URL);
      Map<String, Object> data = advanced ? detailedSearchPayload(request, query) : searchPayload(query);
      sendPost(client, BASE_URL + "/arama", objectMapper.writeValueAsString(Map.of("data", data)));
      data.put("pageSize", String.valueOf(limit));
      data.put("pageNumber", String.valueOf(page));
      String json = sendPost(client, BASE_URL + "/aramalist", objectMapper.writeValueAsString(Map.of("data", data)));
      return new PrecedentBatchPageResult(
          PrecedentSearchSupport.applyAdvancedFilters(request, parseResults(json)),
          hasMore(json, limit)
      );
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Danistay karar arama servisine baglanilamadi: " + exception.getMessage(), exception);
    }
  }

  public PrecedentDto getDocument(String documentId) {
    String normalizedDocumentId = normalizeDocumentId(documentId);
    try {
      String html = sendGetDocument(normalizedDocumentId, "");
      String rawContent = extractContent(html);
      String contentHtml = PrecedentHtmlSupport.sanitizeHtml(rawContent);
      String plainText = PrecedentHtmlSupport.toPlainText(contentHtml);
      Header header = parseHeader(plainText);
      String topic = header.chamber().isBlank()
          ? "Danistay karari"
          : header.chamber() + " - " + docketLabel(header.docketNo(), header.decisionNo());
      String subject = PrecedentTextSupport.extractSubject(plainText);
      String outcome = PrecedentTextSupport.extractOutcome(plainText);
      String summary = subject.isBlank() ? preview(plainText, 650) : subject;
      return new PrecedentDto(
          normalizedDocumentId,
          "Danistay",
          header.chamber(),
          header.docketNo(),
          header.decisionNo(),
          null,
          topic,
          summary,
          contentHtml.isBlank() ? plainText : contentHtml,
          outcome.isBlank() ? null : outcome
      );
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Danistay karar detayi alinamadi: " + exception.getMessage(), exception);
    }
  }

  public PrecedentSyncResponse sync(PrecedentSyncRequest request) {
    validateCourt(request.court(), "Danistay");
    SyncWindow window = resolveSyncWindow(request);
    int pageSize = normalizeSyncLimit(request.pageSize());
    int maxPages = normalizeSyncPages(request.maxPages());
    Set<String> seenIds = new LinkedHashSet<>();
    List<KnowledgeDocumentRequest> documents = new ArrayList<>();
    BasicCookieStore cookieStore = new BasicCookieStore();

    try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
      sendGet(client, BASE_URL);
      for (int page = 1; page <= maxPages; page++) {
        Map<String, Object> data = syncSearchPayload(request);
        sendPost(client, BASE_URL + "/arama", objectMapper.writeValueAsString(Map.of("data", data)));
        data.put("pageSize", String.valueOf(pageSize));
        data.put("pageNumber", String.valueOf(page));
        String json = sendPost(client, BASE_URL + "/aramalist", objectMapper.writeValueAsString(Map.of("data", data)));
        List<PrecedentDto> rows = parseResults(json);
        if (rows.isEmpty()) {
          break;
        }

        boolean reachedLowerBound = false;
        for (PrecedentDto row : rows) {
          LocalDateTime decisionTime = parseDateTime(row.date());
          if (decisionTime != null) {
            if (decisionTime.isAfter(window.to())) {
              continue;
            }
            if (decisionTime.isBefore(window.from())) {
              reachedLowerBound = true;
              break;
            }
          }

          if (!seenIds.add(row.sourceId())) {
            continue;
          }

          PrecedentDto detail = getDocument(row.sourceId());
          documents.add(toKnowledgeDocument(detail));
        }

        if (reachedLowerBound || rows.size() < pageSize) {
          break;
        }
      }

      if (documents.isEmpty()) {
        return new PrecedentSyncResponse(
            "Danistay",
            window.from().atZone(ISTANBUL),
            window.to().atZone(ISTANBUL),
            0,
            0,
            "",
            "Secilen aralikta karar bulunamadi."
        );
      }

      KnowledgeIngestResponse ingestResponse = aiServiceClient.ingestKnowledge(new KnowledgeIngestRequest(documents));
      return new PrecedentSyncResponse(
          "Danistay",
          window.from().atZone(ISTANBUL),
          window.to().atZone(ISTANBUL),
          documents.size(),
          ingestResponse.indexed(),
          ingestResponse.storage(),
          ingestResponse.message()
      );
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Danistay karar senkronu tamamlanamadi: " + exception.getMessage(), exception);
    }
  }

  private Map<String, Object> searchPayload(String query) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("aranan", query);
    if (!query.isBlank()) {
      data.put("andKelimeler", List.of("\"" + query + "\""));
    } else {
      data.put("andKelimeler", List.of());
    }
    data.put("orKelimeler", List.of());
    data.put("notAndKelimeler", List.of());
    data.put("notOrKelimeler", List.of());
    data.put("siralama", "3");
    data.put("siralamaDirection", "desc");
    return data;
  }

  private Map<String, Object> detailedSearchPayload(PrecedentSearchRequest request, String query) {
    Map<String, Object> data = searchPayload(query);
    if (request.chamber() != null && !request.chamber().isBlank()) {
      data.put("daire", request.chamber().trim());
    }
    if (request.docketNo() != null && !request.docketNo().isBlank()) {
      data.put("esas", request.docketNo().trim());
    }
    if (request.decisionNo() != null && !request.decisionNo().isBlank()) {
      data.put("karar", request.decisionNo().trim());
    }
    String dateFrom = PrecedentSearchSupport.normalizeDate(request.dateFrom());
    String dateTo = PrecedentSearchSupport.normalizeDate(request.dateTo());
    if (!dateFrom.isBlank()) {
      data.put("baslangicTarihi", dateFrom);
    }
    if (!dateTo.isBlank()) {
      data.put("bitisTarihi", dateTo);
    }
    return data;
  }

  private List<PrecedentDto> parseResults(String json) throws IOException {
    JsonNode rows = objectMapper.readTree(json).path("data").path("data");
    if (!rows.isArray()) {
      return List.of();
    }
    List<PrecedentDto> results = new ArrayList<>();
    for (JsonNode row : rows) {
      String id = text(row, "id");
      if (id.isBlank()) {
        continue;
      }
      String chamber = text(row, "daireKurul");
      String docketNo = text(row, "esasNo");
      String decisionNo = text(row, "kararNo");
      String date = text(row, "kararTarihi");
      String topic = chamber.isBlank()
          ? docketLabel(docketNo, decisionNo)
          : chamber + " - " + docketLabel(docketNo, decisionNo);
      results.add(new PrecedentDto(
          id,
          "Danistay",
          chamber,
          docketNo,
          decisionNo,
          date.isBlank() ? null : date,
          topic,
          "",
          null,
          null
      ));
    }
    return results;
  }

  private boolean hasMore(String json, int limit) throws IOException {
    JsonNode rows = objectMapper.readTree(json).path("data").path("data");
    return rows.isArray() && rows.size() >= limit;
  }

  private String sendGet(CloseableHttpClient client, String url) throws IOException, ParseException {
    HttpGet request = new HttpGet(url);
    addCommonHeaders(request);
    try (org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response = client.execute(request)) {
      return readResponse(response);
    }
  }

  private String sendPost(CloseableHttpClient client, String url, String body) throws IOException, ParseException {
    HttpPost request = new HttpPost(url);
    addCommonHeaders(request);
    request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
    try (org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response = client.execute(request)) {
      return readResponse(response);
    }
  }

  private String sendGetDocument(String documentId, String queryHint) throws IOException, ParseException {
    BasicCookieStore cookieStore = new BasicCookieStore();
    try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
      sendGet(client, BASE_URL);
      String url = BASE_URL + "/getDokuman?id=" + java.net.URLEncoder.encode(documentId, StandardCharsets.UTF_8) + "&arananKelime=" + java.net.URLEncoder.encode(queryHint, StandardCharsets.UTF_8);
      return sendGet(client, url);
    }
  }

  private void addCommonHeaders(org.apache.hc.core5.http.HttpMessage request) {
    request.addHeader("Accept", "application/json, text/html, */*");
    request.addHeader("User-Agent", "LawAI/1.0 (+https://karararama.danistay.gov.tr)");
    request.addHeader("Referer", BASE_URL + "/");
  }

  private String readResponse(org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response) throws IOException, ParseException {
    String body = response.getEntity() == null
        ? ""
        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
    if (response.getCode() >= 400) {
      throw new IllegalStateException("Danistay servisi " + response.getCode() + " dondu: " + preview(body, 300));
    }
    return body;
  }

  private String normalizeDocumentId(String documentId) {
    String normalized = documentId == null ? "" : documentId.trim();
    if (!normalized.matches("\\d{3,20}")) {
      throw new IllegalArgumentException("Gecersiz Danistay karar ID.");
    }
    return normalized;
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private String text(JsonNode node, String field) {
    return cleanText(node.path(field).asText("").trim());
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
    text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
    return text.trim();
  }

  private String extractContent(String html) {
    Matcher matcher = DOCUMENT_PATTERN.matcher(html == null ? "" : html);
    if (!matcher.find()) {
      return html == null ? "" : html;
    }
    return org.springframework.web.util.HtmlUtils.htmlUnescape(matcher.group(1));
  }

  private Header parseHeader(String content) {
    if (content == null || content.isBlank()) {
      return new Header("Danistay", "", "");
    }
    Matcher matcher = HEADER_PATTERN.matcher(content.trim());
    if (!matcher.find()) {
      return new Header("Danistay", "", "");
    }
    return new Header(
        cleanText(matcher.group(1)),
        cleanText(matcher.group(2)),
        cleanText(matcher.group(3))
    );
  }

  private String docketLabel(String docketNo, String decisionNo) {
    if (docketNo.isBlank() && decisionNo.isBlank()) {
      return "";
    }
    if (docketNo.isBlank()) {
      return decisionNo;
    }
    if (decisionNo.isBlank()) {
      return docketNo;
    }
    return docketNo + " / " + decisionNo;
  }

  private String preview(String content, int maxLength) {
    if (content == null || content.length() <= maxLength) {
      return content;
    }
    return content.substring(0, maxLength) + "...";
  }

  private KnowledgeDocumentRequest toKnowledgeDocument(PrecedentDto precedent) {
    return new KnowledgeDocumentRequest(
        "precedent",
        precedent.court(),
        precedent.chamber(),
        precedent.docketNo(),
        precedent.decisionNo(),
        precedent.date(),
        precedent.topic(),
        precedent.summary() == null ? "" : precedent.summary(),
        precedent.content() == null ? "" : precedent.content()
    );
  }

  private Map<String, Object> syncSearchPayload(PrecedentSyncRequest request) {
    Map<String, Object> data = searchPayload("");
    String dateFrom = normalizeSyncDate(request.dateFrom());
    String dateTo = normalizeSyncDate(request.dateTo());
    if (!dateFrom.isBlank()) {
      data.put("baslangicTarihi", dateFrom);
    }
    if (!dateTo.isBlank()) {
      data.put("bitisTarihi", dateTo);
    }
    return data;
  }

  private SyncWindow resolveSyncWindow(PrecedentSyncRequest request) {
    LocalDateTime to = parseDateTime(request.dateTo());
    LocalDateTime from = parseDateTime(request.dateFrom());
    Integer minutesBack = request.minutesBack();
    if (to == null) {
      to = LocalDateTime.now(ISTANBUL);
    }
    if (from == null && minutesBack != null && minutesBack > 0) {
      from = to.minusMinutes(minutesBack);
    }
    if (from == null) {
      throw new IllegalArgumentException("Senkron icin baslangic zamani girin veya minutesBack kullanin.");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("Bitis zamani baslangic zamanindan once olamaz.");
    }
    return new SyncWindow(from, to);
  }

  private LocalDateTime parseDateTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    try {
      return LocalDateTime.parse(trimmed);
    } catch (Exception ignored) {
      // try next format
    }
    try {
      return LocalDate.parse(trimmed).atStartOfDay();
    } catch (Exception ignored) {
      return null;
    }
  }

  private String normalizeSyncDate(String value) {
    return value == null ? "" : value.trim();
  }

  private int normalizeSyncLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return SYNC_PAGE_LIMIT;
    }
    return Math.min(limit, SYNC_PAGE_LIMIT);
  }

  private int normalizeSyncPages(Integer maxPages) {
    if (maxPages == null || maxPages <= 0) {
      return SYNC_MAX_PAGES;
    }
    return Math.min(maxPages, SYNC_MAX_PAGES);
  }

  private void validateCourt(String court, String expected) {
    if (court == null || court.isBlank()) {
      return;
    }
    String normalized = court.trim().toLowerCase(Locale.ROOT);
    if (!normalized.contains(expected.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Bu endpoint sadece " + expected + " icin calisir.");
    }
  }

  private record Header(String chamber, String docketNo, String decisionNo) {
  }

  private record SyncWindow(LocalDateTime from, LocalDateTime to) {
  }
}
