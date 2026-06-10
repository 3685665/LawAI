package com.lawai.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.services")
public record ServiceClientsProperties(
    String authBaseUrl,
    String platformBaseUrl
) {
  public ServiceClientsProperties {
    if (authBaseUrl == null || authBaseUrl.isBlank()) {
      authBaseUrl = "http://localhost:8081";
    }
    if (platformBaseUrl == null || platformBaseUrl.isBlank()) {
      platformBaseUrl = "http://localhost:8086";
    }
  }
}
