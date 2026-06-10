package com.lawai.auth;

import com.lawai.auth.model.AuthenticatedUser;
import com.lawai.auth.security.SessionAuthenticationFilter;
import com.lawai.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/session")
public class InternalSessionController {

  private final AuthService authService;

  public InternalSessionController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping("/validate")
  public ResponseEntity<com.lawai.common.model.AuthenticatedUser> validate(HttpServletRequest request) {
    String token = extractCookie(request);
    if (token == null) {
      return ResponseEntity.status(401).build();
    }
    try {
      AuthenticatedUser user = authService.requireAuthenticatedUser(token);
      return ResponseEntity.ok(new com.lawai.common.model.AuthenticatedUser(user.id(), user.name(), user.email(), user.role()));
    } catch (RuntimeException ex) {
      return ResponseEntity.status(401).build();
    }
  }

  private String extractCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (SessionAuthenticationFilter.SESSION_COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
