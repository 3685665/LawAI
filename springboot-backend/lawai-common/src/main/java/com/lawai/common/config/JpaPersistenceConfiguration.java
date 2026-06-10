package com.lawai.common.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.lawai.persistence.repository")
@EntityScan(basePackages = "com.lawai.persistence.entity")
public class JpaPersistenceConfiguration {
}
