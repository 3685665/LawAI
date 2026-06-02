package com.lawai.auth;

import com.lawai.auth.dto.AuthRegisterRequest;
import com.lawai.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrap {

  private final AuthService authService;
  private final boolean enabled;
  private final String name;
  private final String email;
  private final String password;

  public AuthBootstrap(
      AuthService authService,
      @Value("${app.auth.bootstrap-enabled:true}") boolean enabled,
      @Value("${app.auth.bootstrap-name:Yonetici}") String name,
      @Value("${app.auth.bootstrap-email:admin@lawai.local}") String email,
      @Value("${app.auth.bootstrap-password:ChangeMe123!}") String password
  ) {
    this.authService = authService;
    this.enabled = enabled;
    this.name = name;
    this.email = email;
    this.password = password;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seedDefaultUser() {
    if (!enabled || authService.hasUsers()) {
      return;
    }
    authService.register(new AuthRegisterRequest(name, email, password));
  }
}
