package com.lawai;

import com.lawai.common.config.CommonAutoConfiguration;
import com.lawai.common.config.JpaPersistenceConfiguration;
import com.lawai.config.AppProperties;
import com.lawai.config.ResearchProperties;
import com.lawai.document.service.DocumentProcessingProperties;
import com.lawai.document.service.OpenSearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.lawai.api", "com.lawai.document", "com.lawai.persistence", "com.lawai.config"})
@EnableConfigurationProperties({AppProperties.class, ResearchProperties.class, DocumentProcessingProperties.class, OpenSearchProperties.class})
@Import({CommonAutoConfiguration.class, JpaPersistenceConfiguration.class})
public class ResearchServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ResearchServiceApplication.class, args);
  }
}
