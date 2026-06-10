package com.lawai;

import com.lawai.common.config.CommonAutoConfiguration;
import com.lawai.common.config.JpaPersistenceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.lawai.api", "com.lawai.persistence", "com.lawai.config"})
@Import({CommonAutoConfiguration.class, JpaPersistenceConfiguration.class})
public class PlatformServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(PlatformServiceApplication.class, args);
  }
}
