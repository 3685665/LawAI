package com.lawai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.research")
public record ResearchProperties(
    boolean mockEnabled,
    int legislationSearchLimit,
    int caseLawSearchLimit,
    int webSearchLimit
) {
  public ResearchProperties {
    if (legislationSearchLimit <= 0) {
      legislationSearchLimit = 5;
    }
    if (caseLawSearchLimit <= 0) {
      caseLawSearchLimit = 5;
    }
    if (webSearchLimit <= 0) {
      webSearchLimit = 3;
    }
  }
}
