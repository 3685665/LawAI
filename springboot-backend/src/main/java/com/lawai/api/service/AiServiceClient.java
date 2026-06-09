package com.lawai.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.ChatRequest;
import com.lawai.api.dto.ChatResponse;
import com.lawai.api.dto.KnowledgeIngestRequest;
import com.lawai.api.dto.KnowledgeIngestResponse;
import com.lawai.api.dto.PetitionRequest;
import com.lawai.api.dto.PetitionResponse;
import com.lawai.api.dto.PrecedentApplyRequest;
import com.lawai.api.dto.PrecedentApplyResponse;
import com.lawai.api.dto.PrecedentSummarizeRequest;
import com.lawai.api.dto.PrecedentSummarizeResponse;
import com.lawai.api.research.dto.LegalResearchSynthesizeRequest;
import com.lawai.api.research.dto.LegalResearchSynthesizeResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AiServiceClient {

  private final CloseableHttpClient httpClient;
  private final String aiBaseUrl;
  private final ObjectMapper objectMapper;

  public AiServiceClient(@Value("${app.ai-base-url:http://localhost:8000/api}") String aiBaseUrl, ObjectMapper objectMapper) {
    this.httpClient = HttpClients.createDefault();
    this.aiBaseUrl = aiBaseUrl;
    this.objectMapper = objectMapper;
  }

  public Map<?, ?> health() {
    String response = send(new HttpGet(resolve("/health")));
    return fromJson(response, Map.class);
  }

  public ChatResponse chat(ChatRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("question", request.question());
    payload.put("mode", request.mode());
    payload.put("privateMode", request.privateMode());
    String response = send(post("/chat", toJson(payload)));
    return fromJson(response, ChatResponse.class);
  }

  public PrecedentSummarizeResponse summarizePrecedent(PrecedentSummarizeRequest request) {
    String response = send(post("/precedents/summarize", toJson(request)));
    return fromJson(response, PrecedentSummarizeResponse.class);
  }

  public PrecedentApplyResponse applyPrecedentToPetition(PrecedentApplyRequest request) {
    String response = send(post("/precedents/apply-to-petition", toJson(request)));
    return fromJson(response, PrecedentApplyResponse.class);
  }

  public PetitionResponse generatePetition(PetitionRequest request) {
    String response = send(post("/petitions", toJson(request)));
    return fromJson(response, PetitionResponse.class);
  }

  public KnowledgeIngestResponse ingestKnowledge(KnowledgeIngestRequest request) {
    String response = send(post("/knowledge/documents", toJson(request)));
    return fromJson(response, KnowledgeIngestResponse.class);
  }

  public KnowledgeIngestResponse seedPrecedents() {
    String response = send(post("/knowledge/seed-precedents", null));
    return fromJson(response, KnowledgeIngestResponse.class);
  }

  public LegalResearchSynthesizeResponse synthesizeResearch(LegalResearchSynthesizeRequest request) {
    String response = send(post("/research/synthesize", toJson(request)));
    return fromJson(response, LegalResearchSynthesizeResponse.class);
  }

  private String send(HttpUriRequestBase request) {
    try (org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response = httpClient.execute(request)) {
      int statusCode = response.getCode();
      String body = response.getEntity() == null ? "" : org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity());
      if (statusCode >= 400) {
        throw new IllegalStateException("AI servisi " + statusCode + " dondu: " + body);
      }
      return body;
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("AI servisine baglanilamadi: " + exception.getMessage(), exception);
    }
  }

  private HttpUriRequestBase post(String path, String body) {
    HttpPost request = new HttpPost(resolve(path));
    if (body != null) {
      request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }
    return request;
  }

  private URI resolve(String path) {
    return URI.create(aiBaseUrl + path);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JSON serialize edilemedi: " + exception.getMessage(), exception);
    }
  }

  private <T> T fromJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JSON parse edilemedi: " + exception.getMessage() + " | body=" + json, exception);
    }
  }
}
