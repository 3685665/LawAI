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
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    String query = normalizeQuery(request.query());
    int limit = normalizeLimit(request.limit());
    BasicCookieStore cookieStore = new BasicCookieStore();
    try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
      sendGet(client, BASE_URL);
      List<YargitayRow> rows = searchRows(client, query, limit);
      List<PrecedentDto> results = new ArrayList<>();
      for (YargitayRow row : rows) {
        String content = getDocumentText(client, row.id());
        results.add(toPrecedent(row, content));
      }
      return results;
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Yargitay karar arama servisine baglanilamadi: " + exception.getMessage(), exception);
    }
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

  private String getDocumentText(CloseableHttpClient client, String documentId) {
    try {
      String url = BASE_URL + "/getDokuman?id=" + URLEncoder.encode(documentId, StandardCharsets.UTF_8);
      String json = sendGet(client, url);
      String html = objectMapper.readTree(json).path("data").asText("");
      return htmlToText(html);
    } catch (IOException | ParseException exception) {
      return "Karar metni alinamadi: " + exception.getMessage();
    }
  }

  private PrecedentDto toPrecedent(YargitayRow row, String content) {
    String chamber = row.chamber().isBlank() ? "Yargitay" : row.chamber();
    String title = chamber + " - " + docketLabel(row.docketNo(), "E.") + " / " + docketLabel(row.decisionNo(), "K.");
    String normalizedContent = content.isBlank() ? title : content;
    return new PrecedentDto(
        "Yargitay",
        chamber,
        docketLabel(row.docketNo(), "E."),
        docketLabel(row.decisionNo(), "K."),
        row.date().isBlank() ? null : row.date(),
        title,
        preview(normalizedContent, 650),
        normalizedContent
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
      throw new IllegalStateException("Yargitay servisi " + response.getCode() + " dondu: " + preview(body, 300));
    }
    return body;
  }

  private String normalizeQuery(String query) {
    String normalized = query == null ? "" : query.trim();
    if (normalized.length() < 3) {
      throw new IllegalArgumentException("Ictihat aramasi icin en az 3 karakter girin.");
    }
    return normalized;
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private String htmlToText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    String text = html
        .replaceAll("(?is)<script.*?</script>", " ")
        .replaceAll("(?is)<style.*?</style>", " ")
        .replaceAll("(?i)<br\\s*/?>", "\n")
        .replaceAll("(?i)</p>", "\n")
        .replaceAll("(?i)</li>", "\n")
        .replaceAll("(?s)<[^>]+>", " ");
    text = HtmlUtils.htmlUnescape(text);
    text = text.replace('\u00a0', ' ');
    text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
    text = text.replaceAll("\\n\\s+", "\n");
    text = text.replaceAll("\\n{3,}", "\n\n");
    return text.trim();
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
    return node.path(field).asText("").trim();
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
