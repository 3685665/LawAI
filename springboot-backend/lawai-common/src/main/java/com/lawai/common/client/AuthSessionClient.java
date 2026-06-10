package com.lawai.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.common.config.ServiceClientsProperties;
import com.lawai.common.model.AuthenticatedUser;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

@Component
public class AuthSessionClient {

  private final ServiceClientsProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public AuthSessionClient(ServiceClientsProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public Optional<AuthenticatedUser> validateSession(String sessionToken) {
    if (sessionToken == null || sessionToken.isBlank()) {
      return Optional.empty();
    }
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(properties.authBaseUrl() + "/internal/session/validate"))
          .header("Cookie", "LAI_SESSION=" + sessionToken)
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(response.body(), AuthenticatedUser.class));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }
}
