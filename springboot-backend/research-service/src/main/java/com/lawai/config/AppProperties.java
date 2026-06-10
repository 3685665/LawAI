package com.lawai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String aiBaseUrl, int maxUploadMb) {
}
