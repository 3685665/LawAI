package com.lawai.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public class SmtpEnvFileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  public static final String PROPERTY_SOURCE_NAME = "lawaiSmtpEnvFile";
  public static final String CONFIG_SOURCE_PROPERTY = "lawai.smtp.config-source";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    for (Path candidate : candidateFiles()) {
      if (!Files.isRegularFile(candidate)) {
        continue;
      }
      Map<String, Object> properties = loadProperties(candidate);
      if (properties.isEmpty()) {
        continue;
      }
      environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
      environment.getSystemProperties().put(CONFIG_SOURCE_PROPERTY, candidate.toAbsolutePath().normalize().toString());
      return;
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 15;
  }

  private List<Path> candidateFiles() {
    Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    Path moduleRoot = resolveModuleRoot(cwd);
    return Stream.of(
            moduleRoot.resolve(".env.smtp"),
            cwd.resolve(".env.smtp"),
            cwd.resolve("springboot-backend/.env.smtp"),
            cwd.getParent() == null ? null : cwd.getParent().resolve("springboot-backend/.env.smtp")
        )
        .filter(path -> path != null)
        .map(Path::normalize)
        .distinct()
        .toList();
  }

  private Path resolveModuleRoot(Path start) {
    Path current = start;
    for (int depth = 0; depth < 6 && current != null; depth++) {
      if (isSpringBootModule(current)) {
        return current;
      }
      Path nested = current.resolve("springboot-backend");
      if (isSpringBootModule(nested)) {
        return nested;
      }
      current = current.getParent();
    }
    return start;
  }

  private boolean isSpringBootModule(Path directory) {
    return Files.isRegularFile(directory.resolve("pom.xml"))
        && (Files.isRegularFile(directory.resolve("mvnw")) || Files.isRegularFile(directory.resolve("mvnw.cmd")));
  }

  private Map<String, Object> loadProperties(Path file) {
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    } catch (IOException ignored) {
      return Map.of();
    }

    Map<String, Object> normalized = new LinkedHashMap<>();
    properties.forEach((key, value) -> {
      if (key == null || value == null) {
        return;
      }
      String propertyKey = key.toString().trim();
      String propertyValue = value.toString().trim();
      if (StringUtils.hasText(propertyKey) && StringUtils.hasText(propertyValue)) {
        normalized.put(propertyKey, propertyValue);
      }
    });
    return normalized;
  }
}
