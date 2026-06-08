package com.lawai.api.service;

import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnayasaPrecedentService {

  private static final String BASE_URL = "https://kararlarbilgibankasi.anayasa.gov.tr";
  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;
  private static final int MAX_PAGES = 5;

  private static final Pattern CARD_PATTERN = Pattern.compile(
      "<div class=\"birkarar col-sm-12\">(.*?)</div></a></a></div>",
      Pattern.DOTALL
  );
  private static final Pattern HREF_PATTERN = Pattern.compile(
      "href=\"https://kararlarbilgibankasi\\.anayasa\\.gov\\.tr/BB/(\\d{4})/(\\d+)\"",
      Pattern.CASE_INSENSITIVE
  );
  private static final Pattern TITLE_PATTERN = Pattern.compile(
      "<titles[^>]*>(.*?)</titles>",
      Pattern.DOTALL | Pattern.CASE_INSENSITIVE
  );
  private static final Pattern INFO_PATTERN = Pattern.compile(
      "<div class=\"kararbilgileri\">(.*?)</div>",
      Pattern.DOTALL | Pattern.CASE_INSENSITIVE
  );
  private static final Pattern SUMMARY_PATTERN = Pattern.compile(
      "<div class=\"basvurukonualani\">(.*?)</div>",
      Pattern.DOTALL | Pattern.CASE_INSENSITIVE
  );

  public List<PrecedentDto> search(PrecedentSearchRequest request) {
    String query = normalizeQuery(request.query());
    int limit = normalizeLimit(request.limit());
    List<PrecedentDto> results = new ArrayList<>();
    try {
      for (int page = 1; page <= MAX_PAGES && results.size() < limit; page++) {
        String html = loadPage(query, page);
        List<PrecedentDto> pageResults = parseResults(html);
        if (pageResults.isEmpty()) {
          break;
        }
        results.addAll(pageResults);
      }
      return results.stream()
          .distinct()
          .limit(limit)
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("Anayasa Mahkemesi karar arama servisine baglanilamadi: " + exception.getMessage(), exception);
    }
  }

  private String loadPage(String query, int page) throws IOException {
    String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
    String url = BASE_URL + "/Ara?KelimeAra%5B%5D=" + encodedQuery + "&page=" + page;
    return HttpClientSupport.get(url);
  }

  private List<PrecedentDto> parseResults(String html) {
    if (html == null || html.isBlank()) {
      return List.of();
    }
    List<PrecedentDto> results = new ArrayList<>();
    Matcher cardMatcher = CARD_PATTERN.matcher(html);
    while (cardMatcher.find()) {
      String cardHtml = cardMatcher.group(1);
      Matcher hrefMatcher = HREF_PATTERN.matcher(cardHtml);
      if (!hrefMatcher.find()) {
        continue;
      }
      String year = hrefMatcher.group(1);
      String number = hrefMatcher.group(2);
      String sourceId = "BB/" + year + "/" + number;

      String title = firstGroup(TITLE_PATTERN, cardHtml);
      String info = firstGroup(INFO_PATTERN, cardHtml);
      String summary = firstGroup(SUMMARY_PATTERN, cardHtml);

      String docketNo = extractFirstToken(info);
      String decisionNo = extractDecisionType(info);
      String chamber = extractChamber(info);
      String date = extractDecisionDate(info);

      if (docketNo.isBlank() || title.isBlank()) {
        continue;
      }

      results.add(new PrecedentDto(
          sourceId,
          "Anayasa Mahkemesi",
          chamber,
          docketNo,
          decisionNo,
          date.isBlank() ? null : date,
          cleanText(title),
          cleanText(summary),
          null
      ));
    }
    return results;
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

  private String firstGroup(Pattern pattern, String value) {
    Matcher matcher = pattern.matcher(value);
    if (!matcher.find()) {
      return "";
    }
    return cleanText(matcher.group(1));
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

  private String extractFirstToken(String info) {
    if (info == null || info.isBlank()) {
      return "";
    }
    String[] parts = info.split("\\|");
    if (parts.length == 0) {
      return "";
    }
    return cleanText(parts[0]);
  }

  private String extractDecisionType(String info) {
    if (info == null || info.isBlank()) {
      return "";
    }
    String[] parts = info.split("\\|");
    if (parts.length < 2) {
      return "";
    }
    return cleanText(parts[1]);
  }

  private String extractChamber(String info) {
    if (info == null || info.isBlank()) {
      return "";
    }
    String[] parts = info.split("\\|");
    if (parts.length < 3) {
      return "";
    }
    return cleanText(parts[2]);
  }

  private String extractDecisionDate(String info) {
    if (info == null || info.isBlank()) {
      return "";
    }
    Matcher matcher = Pattern.compile("Karar Tarihi\\s*:\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(info);
    if (!matcher.find()) {
      return "";
    }
    return cleanText(matcher.group(1));
  }

  private static final class HttpClientSupport {
    private HttpClientSupport() {
    }

    private static String get(String url) throws IOException {
      java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
          .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
          .build();
      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          .header("User-Agent", "LawAI/1.0")
          .GET()
          .build();
      try {
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
          throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Karar servisi istegi kesildi.", exception);
      }
    }
  }
}
