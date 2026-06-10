package com.lawai.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class YargitayPrecedentService {

  private static final String BASE_URL = "https://karararama.yargitay.gov.tr";
  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;

  private final ObjectMapper objectMapper;

  public YargitayPrecedentService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<PrecedentDto> search(PrecedentSearchRequest request) {
    boolean advanced = PrecedentSearchSupport.isAdvanced(request);
    String query = PrecedentSearchSupport.normalizeOptionalQuery(request.query(), advanced);
    int limit = normalizeLimit(request.limit());
    BasicCookieStore cookieStore = new BasicCookieStore();
    try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
      sendGet(client, BASE_URL);
      List<YargitayRow> rows = advanced
          ? searchDetailedRows(client, request, query, limit)
          : searchRows(client, query, limit);
      List<PrecedentDto> results = new ArrayList<>();
      for (YargitayRow row : rows) {
        results.add(toListPrecedent(row));
      }
      return PrecedentSearchSupport.applyAdvancedFilters(request, results);
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Yargitay karar arama servisine baglanilamadi: " + exception.getMessage(), exception);
    }
  }

  public PrecedentDto getDocument(String documentId) {
    String normalizedId = normalizeDocumentId(documentId);
    BasicCookieStore cookieStore = new BasicCookieStore();
    try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
      sendGet(client, BASE_URL);
      String rawHtml = repairMojibake(getDocumentHtml(client, normalizedId));
      String contentHtml = PrecedentHtmlSupport.sanitizeHtml(rawHtml);
      String plainText = repairMojibake(PrecedentHtmlSupport.toPlainText(contentHtml));
      YargitayHeader header = parseHeader(plainText);
      return toDetailPrecedent(new YargitayRow(
          normalizedId,
          header.chamber(),
          header.docketNo(),
          header.decisionNo(),
          "",
          ""
      ), contentHtml, plainText);
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Yargitay karar detayi alinamadi: " + exception.getMessage(), exception);
    }
  }

  private List<YargitayRow> searchDetailedRows(
      CloseableHttpClient client,
      PrecedentSearchRequest request,
      String query,
      int limit
  ) throws IOException, ParseException {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("arananKelime", query);
    data.put("baslangicTarihi", PrecedentSearchSupport.normalizeDate(request.dateFrom()));
    data.put("bitisTarihi", PrecedentSearchSupport.normalizeDate(request.dateTo()));
    data.put("birimYrgKurulDaire", "");
    data.put("birimYrgHukukDaire", "");
    data.put("birimYrgCezaDaire", "");
    applyChamber(data, request.chamber());

    String[] esas = PrecedentSearchSupport.parseDocketParts(request.docketNo());
    String[] karar = PrecedentSearchSupport.parseDocketParts(request.decisionNo());
    data.put("esasYil", esas[0]);
    data.put("esasIlkSiraNo", esas[1]);
    data.put("esasSonSiraNo", "");
    data.put("kararYil", karar[0]);
    data.put("kararIlkSiraNo", karar[1]);
    data.put("kararSonSiraNo", "");
    data.put("pageSize", String.valueOf(limit));
    data.put("pageNumber", "1");
    data.put("siralama", "3");
    data.put("siralamaDirection", "desc");

    String json = sendPost(client, BASE_URL + "/aramadetaylist", objectMapper.writeValueAsString(Map.of("data", data)));
    return parseRows(json);
  }

  private void applyChamber(Map<String, Object> data, String chamber) {
    if (chamber == null || chamber.isBlank()) {
      return;
    }
    String normalized = chamber.trim();
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.contains("ceza")) {
      data.put("birimYrgCezaDaire", normalized);
      return;
    }
    if (lower.contains("hukuk")) {
      data.put("birimYrgHukukDaire", normalized);
      return;
    }
    data.put("birimYrgKurulDaire", normalized);
  }

  private List<YargitayRow> searchRows(CloseableHttpClient client, String query, int limit) throws IOException, ParseException {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("arananKelime", query);
    data.put("pageSize", String.valueOf(limit));
    data.put("pageNumber", "1");
    data.put("siralama", "3");
    data.put("siralamaDirection", "desc");

    Map<String, Object> payload = Map.of("data", data);
    String json = sendPost(client, BASE_URL + "/aramalist", objectMapper.writeValueAsString(payload));
    return parseRows(json);
  }

  private List<YargitayRow> parseRows(String json) throws IOException {
    JsonNode rows = objectMapper.readTree(json).path("data").path("data");
    if (!rows.isArray()) {
      return List.of();
    }
    List<YargitayRow> results = new ArrayList<>();
    for (JsonNode row : rows) {
      String id = text(row, "id");
      if (id.isBlank()) {
        continue;
      }
      results.add(new YargitayRow(
          id,
          text(row, "daire"),
          text(row, "esasNo"),
          text(row, "kararNo"),
          text(row, "kararTarihi"),
          text(row, "arananKelime")
      ));
    }
    return results;
  }

  private String getDocumentHtml(CloseableHttpClient client, String documentId) throws IOException, ParseException {
    String url = BASE_URL + "/getDokuman?id=" + URLEncoder.encode(documentId, StandardCharsets.UTF_8);
    String json = sendGet(client, url);
    return objectMapper.readTree(json).path("data").asText("");
  }

  private PrecedentDto toListPrecedent(YargitayRow row) {
    String chamber = row.chamber().isBlank() ? "Yargitay" : row.chamber();
    String title = chamber + " - " + docketLabel(row.docketNo(), "E.") + " / " + docketLabel(row.decisionNo(), "K.");
    return new PrecedentDto(
        row.id(),
        "Yargitay",
        chamber,
        docketLabel(row.docketNo(), "E."),
        docketLabel(row.decisionNo(), "K."),
        row.date().isBlank() ? null : row.date(),
        title,
        "",
        null,
        null
    );
  }

  private PrecedentDto toDetailPrecedent(YargitayRow row, String contentHtml, String plainText) {
    String chamber = row.chamber().isBlank() ? "Yargitay" : row.chamber();
    String title = chamber + " - " + docketLabel(row.docketNo(), "E.") + " / " + docketLabel(row.decisionNo(), "K.");
    String normalizedPlain = plainText.isBlank() ? title : plainText;
    String displayHtml = contentHtml.isBlank() ? "" : contentHtml;
    String subject = PrecedentTextSupport.extractSubject(normalizedPlain);
    String outcome = PrecedentTextSupport.extractOutcome(normalizedPlain);
    String summary = subject.isBlank() ? preview(normalizedPlain, 650) : subject;
    return new PrecedentDto(
        row.id(),
        "Yargitay",
        chamber,
        docketLabel(row.docketNo(), "E."),
        docketLabel(row.decisionNo(), "K."),
        row.date().isBlank() ? null : row.date(),
        title,
        summary,
        displayHtml.isBlank() ? normalizedPlain : displayHtml,
        outcome.isBlank() ? null : outcome
    );
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

  private void addCommonHeaders(org.apache.hc.core5.http.HttpMessage request) {
    request.addHeader("Accept", "application/json, text/html, */*");
    request.addHeader("User-Agent", "LawAI/1.0 (+https://karararama.yargitay.gov.tr)");
    request.addHeader("Referer", BASE_URL + "/");
  }

  private String readResponse(org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response) throws IOException, ParseException {
    String body = response.getEntity() == null
        ? ""
        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
    if (response.getCode() >= 400) {
      if (response.getCode() == 429) {
        throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Yargitay erisim siniri asildi. Biraz bekleyip tekrar deneyin; karar detaylari tek tek acildiginda cekilir."
        );
      }
      throw new IllegalStateException("Yargitay servisi " + response.getCode() + " dondu: " + preview(body, 300));
    }
    return body;
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private String normalizeDocumentId(String documentId) {
    String normalized = documentId == null ? "" : documentId.trim();
    if (!normalized.matches("\\d{3,20}")) {
      throw new IllegalArgumentException("Gecersiz Yargitay karar ID.");
    }
    return normalized;
  }

  private String docketLabel(String value, String suffix) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.endsWith(suffix) ? value : value + " " + suffix;
  }

  private String preview(String content, int maxLength) {
    if (content == null || content.length() <= maxLength) {
      return content;
    }
    return content.substring(0, maxLength) + "...";
  }

  private String text(JsonNode node, String field) {
    return repairMojibake(node.path(field).asText("").trim());
  }

  private String repairMojibake(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    if (value.indexOf('\u00c3') < 0 && value.indexOf('\u00c4') < 0 && value.indexOf('\u00c5') < 0) {
      return value;
    }
    return new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
  }

  private YargitayHeader parseHeader(String content) {
    if (content == null || content.isBlank()) {
      return new YargitayHeader("Yargitay", null, null);
    }
    String firstLine = content.lines().findFirst().orElse("").trim();
    java.util.regex.Matcher matcher = java.util.regex.Pattern
        .compile("^(.*?)\\s+(\\d{4}/\\d+)\\s+E\\.\\s*,\\s*(\\d{4}/\\d+)\\s+K\\.")
        .matcher(firstLine);
    if (matcher.find()) {
      return new YargitayHeader(matcher.group(1).trim(), matcher.group(2), matcher.group(3));
    }
    return new YargitayHeader(firstLine.isBlank() ? "Yargitay" : firstLine, null, null);
  }

  private record YargitayHeader(String chamber, String docketNo, String decisionNo) {
  }

  private record YargitayRow(
      String id,
      String chamber,
      String docketNo,
      String decisionNo,
      String date,
      String query
  ) {
  }
}
