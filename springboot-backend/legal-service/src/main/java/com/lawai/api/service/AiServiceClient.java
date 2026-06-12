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
import com.lawai.common.i18n.I18nMessages;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AiServiceClient {

  private final CloseableHttpClient httpClient;
  private final String aiBaseUrl;
  private final ObjectMapper objectMapper;
  private final I18nMessages i18n;

  public AiServiceClient(
      @Value("${app.ai-base-url:http://localhost:8000/api}") String aiBaseUrl,
      ObjectMapper objectMapper,
      I18nMessages i18n
  ) {
    this.httpClient = HttpClients.createDefault();
    this.aiBaseUrl = aiBaseUrl;
    this.objectMapper = objectMapper;
    this.i18n = i18n;
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

  private String send(HttpUriRequestBase request) {
    request.setHeader("Accept-Language", acceptLanguage());
    try (org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response = httpClient.execute(request)) {
      int statusCode = response.getCode();
      String body = response.getEntity() == null ? "" : org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity());
      if (statusCode >= 400) {
        throw new IllegalStateException(i18n.get("error.ai-service-status", statusCode, body));
      }
      return body;
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException(i18n.get("error.ai-service-unavailable", exception.getMessage()), exception);
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

  private String acceptLanguage() {
    Locale locale = LocaleContextHolder.getLocale();
    return "en".equalsIgnoreCase(locale.getLanguage())
        ? "en-US,en;q=0.9,tr;q=0.8"
        : "tr-TR,tr;q=0.9,en;q=0.8";
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(i18n.get("error.json-serialize", exception.getMessage()), exception);
    }
  }

  private <T> T fromJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(i18n.get("error.json-parse", exception.getMessage() + " | body=" + json), exception);
    }
  }
}
