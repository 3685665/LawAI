package com.lawai.auth;

import com.lawai.auth.dto.AuthRegisterRequest;
import com.lawai.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth.bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class AuthBootstrap {

  private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);

  private final AuthService authService;
  private final String name;
  private final String email;
  private final String password;

  public AuthBootstrap(
      AuthService authService,
      @Value("${app.auth.bootstrap-name:Yonetici}") String name,
      @Value("${app.auth.bootstrap-email:admin@lawai.local}") String email,
      @Value("${app.auth.bootstrap-password:ChangeMe123!}") String password
  ) {
    this.authService = authService;
    this.name = name;
    this.email = email;
    this.password = password;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seedDefaultUser() {
    if (authService.hasUsers()) {
      return;
    }

    authService.register(new AuthRegisterRequest(name, email, password));
    log.info("Bootstrap auth account created for {}", email);
  }
}
