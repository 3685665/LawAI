package com.lawai;

import com.lawai.common.config.CommonAutoConfiguration;
import com.lawai.common.config.JpaPersistenceConfiguration;
import com.lawai.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.lawai.api", "com.lawai.persistence", "com.lawai.config"})
@EnableConfigurationProperties(AppProperties.class)
@Import({CommonAutoConfiguration.class, JpaPersistenceConfiguration.class})
public class LegalServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(LegalServiceApplication.class, args);
  }
}
