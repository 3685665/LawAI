package com.lawai.auth;

import com.lawai.common.config.CommonAutoConfiguration;
import com.lawai.common.config.JpaPersistenceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.lawai.auth", "com.lawai.persistence", "com.lawai.config"})
@Import({CommonAutoConfiguration.class, JpaPersistenceConfiguration.class})
public class AuthServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
