package com.lawai.document.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.document.batch.PrecedentCourt;
import com.lawai.document.config.LegalServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class LegalPrecedentClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public LegalPrecedentClient(LegalServiceProperties properties, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder().baseUrl(properties.legalBaseUrl()).build();
  }

  public BatchPageResponse fetchPage(
      PrecedentCourt court,
      String query,
      LocalDate dateFrom,
      LocalDate dateTo,
      int page,
      int pageSize
  ) {
    Map<String, Object> payload = Map.of(
        "court", court.name(),
        "query", query == null ? "" : query,
        "dateFrom", dateFrom == null ? "" : dateFrom.toString(),
        "dateTo", dateTo == null ? "" : dateTo.toString(),
        "page", page,
        "pageSize", pageSize
    );
    return restClient.post()
        .uri("/internal/precedents/batch/page")
        .body(payload)
        .retrieve()
        .body(BatchPageResponse.class);
  }

  public BatchContentResponse fetchContent(PrecedentCourt court, String sourceId) {
    return restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/internal/precedents/batch/content")
            .queryParam("court", court.name())
            .queryParam("sourceId", sourceId)
            .build())
        .retrieve()
        .body(BatchContentResponse.class);
  }

  public record BatchPageItem(String sourceId, String court, String title, String date) {
  }

  public record BatchPageResponse(List<BatchPageItem> items, boolean hasMore) {
  }

  public record BatchContentResponse(String sourceId, String court, String title, String plainText) {
  }
}
