package com.lawai.document.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.opensearch")
public record OpenSearchProperties(
    boolean enabled,
    String baseUrl,
    String index
) {
}
