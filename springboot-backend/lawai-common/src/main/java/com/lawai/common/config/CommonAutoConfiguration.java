package com.lawai.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.lawai.common")
@EnableConfigurationProperties(ServiceClientsProperties.class)
public class CommonAutoConfiguration {
}
