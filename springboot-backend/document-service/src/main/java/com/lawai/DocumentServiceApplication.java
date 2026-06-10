package com.lawai;

import com.lawai.common.config.CommonAutoConfiguration;
import com.lawai.document.service.DocumentProcessingProperties;
import com.lawai.document.service.OpenSearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.lawai.document", "com.lawai.config"})
@EnableConfigurationProperties({DocumentProcessingProperties.class, OpenSearchProperties.class})
@EnableScheduling
@Import(CommonAutoConfiguration.class)
public class DocumentServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(DocumentServiceApplication.class, args);
  }
}
