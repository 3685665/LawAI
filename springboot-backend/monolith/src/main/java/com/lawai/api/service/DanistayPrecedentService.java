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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DanistayPrecedentService {

  private static final String BASE_URL = "https://karararama.danistay.gov.tr";
  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;
  private static final Pattern HEADER_PATTERN = Pattern.compile("^(.*?)\\s+(\\d{4}/\\d+)\\s*E\\.?\\s*,\\s*(\\d{4}/\\d+)\\s*K\\.?\\b", Pattern.DOTALL);
  private static final Pattern DOCUMENT_PATTERN = Pattern.compile("<p id=\"hiddencontent\" style=\"display: none\">(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  private final ObjectMapper objectMapper;

  public DanistayPrecedentService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
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

  public PrecedentDto getDocument(String documentId) {
    String normalizedDocumentId = normalizeDocumentId(documentId);
    try {
      String html = sendGetDocument(normalizedDocumentId, "");
      String content = extractContent(html);
      String cleanContent = cleanText(content);
      Header header = parseHeader(cleanContent);
      String topic = header.chamber().isBlank()
          ? "Danistay karari"
          : header.chamber() + " - " + docketLabel(header.docketNo(), header.decisionNo());
      return new PrecedentDto(
          normalizedDocumentId,
          "Danistay",
          header.chamber(),
          header.docketNo(),
          header.decisionNo(),
          null,
          topic,
          preview(cleanContent, 650),
          cleanContent
      );
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Danistay karar detayi alinamadi: " + exception.getMessage(), exception);
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

  private record Header(String chamber, String docketNo, String decisionNo) {
  }
}
