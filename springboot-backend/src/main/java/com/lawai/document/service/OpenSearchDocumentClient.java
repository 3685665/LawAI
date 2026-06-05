package com.lawai.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.document.dto.DocumentSearchResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenSearchDocumentClient {

  private final OpenSearchProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private boolean ready;

  public OpenSearchDocumentClient(OpenSearchProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  public int indexChunks(String filename, List<StoredChunk> chunks) {
    if (!properties.enabled()) {
      return 0;
    }
    ensureIndex();
    int indexed = 0;
    for (StoredChunk chunk : chunks) {
      Map<String, Object> body = Map.of(
          "documentId", chunk.documentId(),
          "chunkId", chunk.id(),
          "filename", filename,
          "chunkIndex", chunk.chunkIndex(),
          "content", chunk.content()
      );
      request("PUT", "/" + properties.index() + "/_doc/" + chunk.id(), toJson(body));
      indexed++;
    }
    request("POST", "/" + properties.index() + "/_refresh", "");
    return indexed;
  }

  public List<DocumentSearchResult> search(String query, int limit) {
    if (!properties.enabled()) {
      return List.of();
    }
    ensureIndex();
    String body = toJson(Map.of(
        "size", limit,
        "query", Map.of(
            "match", Map.of(
                "content", Map.of("query", query)
            )
        )
    ));
    String response = request("POST", "/" + properties.index() + "/_search", body);
    return parseSearchResponse(response);
  }

  public List<DocumentSearchResult> searchWholeDocuments(String query, int limit) {
    if (!properties.enabled()) {
      return List.of();
    }
    ensureIndex();
    String body = toJson(Map.of(
        "size", Math.max(limit * 5, 5),
        "query", Map.of(
            "match", Map.of(
                "content", Map.of("query", query)
            )
        )
    ));
    String response = request("POST", "/" + properties.index() + "/_search", body);
    LinkedHashMap<Long, ScoredDocumentHit> documentHits = parseDocumentHits(response);
    List<DocumentSearchResult> results = new ArrayList<>();
    for (ScoredDocumentHit hit : documentHits.values()) {
      results.add(loadWholeDocument(hit));
      if (results.size() >= limit) {
        break;
      }
    }
    return results;
  }

  private synchronized void ensureIndex() {
    if (ready || !properties.enabled()) {
      return;
    }
    String body = toJson(Map.of(
        "mappings", Map.of(
            "properties", Map.of(
                "documentId", Map.of("type", "long"),
                "chunkId", Map.of("type", "long"),
                "filename", Map.of("type", "keyword"),
                "chunkIndex", Map.of("type", "integer"),
                "content", Map.of("type", "text")
            )
        )
    ));
    request("PUT", "/" + properties.index(), body, true);
    ready = true;
  }

  private List<DocumentSearchResult> parseSearchResponse(String json) {
    try {
      JsonNode hits = objectMapper.readTree(json).path("hits").path("hits");
      List<DocumentSearchResult> results = new ArrayList<>();
      for (JsonNode hit : hits) {
        JsonNode source = hit.path("_source");
        results.add(new DocumentSearchResult(
            source.path("documentId").asLong(),
            source.path("chunkId").asLong(),
            source.path("filename").asText(),
            source.path("chunkIndex").asInt(),
            source.path("content").asText(),
            hit.path("_score").asDouble()
        ));
      }
      return results;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("OpenSearch cevabi parse edilemedi: " + exception.getMessage(), exception);
    }
  }

  private LinkedHashMap<Long, ScoredDocumentHit> parseDocumentHits(String json) {
    try {
      JsonNode hits = objectMapper.readTree(json).path("hits").path("hits");
      LinkedHashMap<Long, ScoredDocumentHit> documentHits = new LinkedHashMap<>();
      for (JsonNode hit : hits) {
        JsonNode source = hit.path("_source");
        long documentId = source.path("documentId").asLong();
        if (!documentHits.containsKey(documentId)) {
          documentHits.put(documentId, new ScoredDocumentHit(
              documentId,
              source.path("filename").asText(),
              hit.path("_score").asDouble()
          ));
        }
      }
      return documentHits;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("OpenSearch cevabi parse edilemedi: " + exception.getMessage(), exception);
    }
  }

  private DocumentSearchResult loadWholeDocument(ScoredDocumentHit hit) {
    String body = toJson(Map.of(
        "size", 1000,
        "query", Map.of(
            "term", Map.of(
                "documentId", hit.documentId()
            )
        ),
        "sort", List.of(
            Map.of("chunkIndex", Map.of("order", "asc"))
        )
    ));
    String response = request("POST", "/" + properties.index() + "/_search", body);
    try {
      JsonNode hits = objectMapper.readTree(response).path("hits").path("hits");
      String filename = hit.filename();
      StringBuilder content = new StringBuilder();
      for (JsonNode chunkHit : hits) {
        JsonNode source = chunkHit.path("_source");
        filename = source.path("filename").asText(filename);
        if (!content.isEmpty()) {
          content.append("\n\n");
        }
        content.append(source.path("content").asText());
      }
      return new DocumentSearchResult(
          hit.documentId(),
          0,
          filename,
          0,
          content.toString(),
          hit.score()
      );
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("OpenSearch cevabi parse edilemedi: " + exception.getMessage(), exception);
    }
  }

  private String request(String method, String path, String body) {
    return request(method, path, body, false);
  }

  private String request(String method, String path, String body, boolean ignoreAlreadyExists) {
    HttpRequest request = HttpRequest.newBuilder(resolve(path))
        .timeout(Duration.ofSeconds(10))
        .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8))
        .header("Content-Type", "application/json")
        .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() == 400 && ignoreAlreadyExists && response.body().contains("resource_already_exists_exception")) {
        return response.body();
      }
      if (response.statusCode() >= 400) {
        throw new IllegalStateException("OpenSearch " + response.statusCode() + " dondu: " + response.body());
      }
      return response.body();
    } catch (IOException exception) {
      throw new IllegalStateException("OpenSearch baglantisi basarisiz: " + exception.getMessage(), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("OpenSearch istegi kesildi.", exception);
    }
  }

  private URI resolve(String path) {
    return URI.create(properties.baseUrl().replaceAll("/+$", "") + path);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JSON serialize edilemedi: " + exception.getMessage(), exception);
    }
  }

  private record ScoredDocumentHit(long documentId, String filename, double score) {
  }
}
