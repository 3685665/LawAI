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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DanistayPrecedentService {

  private static final String BASE_URL = "https://karararama.danistay.gov.tr";
  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;

  private final ObjectMapper objectMapper;

  public DanistayPrecedentService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<PrecedentDto> search(PrecedentSearchRequest request) {
    String query = normalizeQuery(request.query());
    int limit = normalizeLimit(request.limit());
    BasicCookieStore cookieStore = new BasicCookieStore();

    try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
      sendGet(client, BASE_URL);
      Map<String, Object> data = searchPayload(query);
      sendPost(client, BASE_URL + "/arama", objectMapper.writeValueAsString(Map.of("data", data)));
      data.put("pageSize", String.valueOf(limit));
      data.put("pageNumber", "1");
      String json = sendPost(client, BASE_URL + "/aramalist", objectMapper.writeValueAsString(Map.of("data", data)));
      return parseResults(json);
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Danistay karar arama servisine baglanilamadi: " + exception.getMessage(), exception);
    }
  }

  private Map<String, Object> searchPayload(String query) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("aranan", query);
    data.put("andKelimeler", List.of("\"" + query + "\""));
    data.put("orKelimeler", List.of());
    data.put("notAndKelimeler", List.of());
    data.put("notOrKelimeler", List.of());
    data.put("siralama", "3");
    data.put("siralamaDirection", "desc");
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
          "Karar metnini gormek icin listeden bu karara tiklayin.",
          null
      ));
    }
    return results;
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
}
