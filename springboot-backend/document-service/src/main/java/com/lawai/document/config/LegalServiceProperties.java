package com.lawai.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.services")
public record LegalServiceProperties(
    String legalBaseUrl
) {
  public LegalServiceProperties {
    if (legalBaseUrl == null || legalBaseUrl.isBlank()) {
      legalBaseUrl = "http://localhost:8083";
    }
  }
}
