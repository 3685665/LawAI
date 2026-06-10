package com.lawai.config;

import com.lawai.common.security.MicroserviceSecurityConfig;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SecurityConfig extends MicroserviceSecurityConfig {
  @Override
  protected List<String> publicPaths() {
    return List.of("/api/health", "/api/billing/iyzico/callback", "/api/billing/iyzico/webhook");
  }
}
