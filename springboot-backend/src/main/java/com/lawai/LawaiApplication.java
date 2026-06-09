package com.lawai;

import com.lawai.config.AppProperties;
import com.lawai.document.service.DocumentProcessingProperties;
import com.lawai.document.service.OpenSearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, DocumentProcessingProperties.class, OpenSearchProperties.class, com.lawai.config.ResearchProperties.class})
public class LawaiApplication {

  public static void main(String[] args) {
    SpringApplication.run(LawaiApplication.class, args);
  }
}
