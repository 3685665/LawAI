package com.lawai.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.common.config.ServiceClientsProperties;
import com.lawai.common.model.AuthenticatedUser;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
public class ActivityLogClient {

  private final ServiceClientsProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public ActivityLogClient(ServiceClientsProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public void logBackend(AuthenticatedUser user, String action, String screen, String detail, String path) {
    postInternal(user, Map.of(
        "source", "backend",
        "action", action,
        "screen", screen,
        "detail", detail,
        "path", path
    ));
  }

  public void logFrontend(AuthenticatedUser user, String action, String screen, String detail, String path) {
    postInternal(user, Map.of(
        "source", "frontend",
        "action", action,
        "screen", screen,
        "detail", detail,
        "path", path
    ));
  }

  private void postInternal(AuthenticatedUser user, Map<String, String> payload) {
    if (user == null) {
      return;
    }
    try {
      String body = objectMapper.writeValueAsString(payload);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(properties.platformBaseUrl() + "/internal/activity-logs"))
          .header("Content-Type", "application/json")
          .header("X-User-Id", user.id())
          .header("X-User-Name", user.name())
          .header("X-User-Email", user.email())
          .header("X-User-Role", user.role())
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (Exception ignored) {
      // Activity logging must not break primary flows.
    }
  }
}
