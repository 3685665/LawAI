package com.lawai.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.research.dto.LegalResearchSynthesizeRequest;
import com.lawai.api.research.dto.LegalResearchSynthesizeResponse;
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

  private HttpPost post(String path, String json) {
    HttpPost post = new HttpPost(resolve(path));
    if (json != null) {
      post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    }
    return post;
  }

  private URI resolve(String path) {
    String base = aiBaseUrl.endsWith("/") ? aiBaseUrl.substring(0, aiBaseUrl.length() - 1) : aiBaseUrl;
    String suffix = path.startsWith("/") ? path : "/" + path;
    return URI.create(base + suffix);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JSON serilestirme hatasi", exception);
    }
  }

  private <T> T fromJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JSON ayristirma hatasi: " + json, exception);
    }
  }
}
