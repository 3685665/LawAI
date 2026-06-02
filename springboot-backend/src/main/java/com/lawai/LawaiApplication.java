package com.lawai;

import com.lawai.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class LawaiApplication {

  public static void main(String[] args) {
    SpringApplication.run(LawaiApplication.class, args);
  }
}
