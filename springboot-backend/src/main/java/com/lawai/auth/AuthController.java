package com.lawai.auth;

import com.lawai.auth.dto.AuthChangePasswordRequest;
import com.lawai.auth.dto.AuthForgotPasswordRequest;
import com.lawai.auth.dto.AuthLoginRequest;
import com.lawai.auth.dto.AuthPasswordResetResponse;
import com.lawai.auth.dto.AuthProfileUpdateRequest;
import com.lawai.auth.dto.AuthRegisterRequest;
import com.lawai.auth.dto.AuthSessionResponse;
import com.lawai.auth.dto.AuthUserDto;
import com.lawai.auth.security.SessionAuthenticationFilter;
import com.lawai.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final boolean cookieSecure;

  public AuthController(AuthService authService, @Value("${app.auth.cookie-secure:false}") boolean cookieSecure) {
    this.authService = authService;
    this.cookieSecure = cookieSecure;
  }

  @PostMapping("/register")
  public AuthSessionResponse register(@Valid @RequestBody AuthRegisterRequest request, HttpServletResponse response) {
    authService.register(request);
    String token = authService.issueSessionToken(new AuthLoginRequest(request.email(), request.password(), true));
    setSessionCookie(response, token, true);
    return new AuthSessionResponse(authService.currentUser(token));
  }

  @PostMapping("/login")
  public AuthSessionResponse login(@Valid @RequestBody AuthLoginRequest request, HttpServletResponse response) {
    authService.login(request);
    String token = authService.issueSessionToken(request);
    setSessionCookie(response, token, Boolean.TRUE.equals(request.rememberMe()));
    return new AuthSessionResponse(authService.currentUser(token));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(extractSessionToken(request));
    clearSessionCookie(response);
    return ResponseEntity.ok().body(java.util.Map.of("message", "Cikis yapildi."));
  }

  @GetMapping("/me")
  public AuthUserDto me(HttpServletRequest request) {
    return authService.currentUser(extractSessionToken(request));
  }

  @GetMapping("/users")
  public List<AuthUserDto> users(HttpServletRequest request) {
    return authService.listUsers(extractSessionToken(request));
  }

  @GetMapping("/users/{id}")
  public AuthUserDto user(@PathVariable String id, HttpServletRequest request) {
    return authService.getUser(extractSessionToken(request), id);
  }

  @PutMapping("/me")
  public AuthUserDto updateMe(@Valid @RequestBody AuthProfileUpdateRequest request, HttpServletRequest httpRequest) {
    return authService.updateProfile(extractSessionToken(httpRequest), request);
  }

  @PostMapping("/password/forgot")
  public AuthPasswordResetResponse forgot(@Valid @RequestBody AuthForgotPasswordRequest request) {
    return authService.requestPasswordReset(request);
  }

  @PostMapping("/password/reset")
  public AuthSessionResponse reset(@Valid @RequestBody com.lawai.auth.dto.AuthResetPasswordRequest request, HttpServletResponse response) {
    AuthUserDto user = authService.resetPassword(request.token(), request.newPassword());
    String token = authService.issueSessionToken(new AuthLoginRequest(user.email(), request.newPassword(), true));
    setSessionCookie(response, token, true);
    return new AuthSessionResponse(authService.currentUser(token));
  }

  @PostMapping("/password/change")
  public AuthSessionResponse change(@Valid @RequestBody AuthChangePasswordRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
    AuthUserDto user = authService.changePassword(extractSessionToken(httpRequest), request);
    String token = authService.issueSessionToken(new AuthLoginRequest(user.email(), request.newPassword(), true));
    setSessionCookie(response, token, true);
    return new AuthSessionResponse(authService.currentUser(token));
  }

  private void setSessionCookie(HttpServletResponse response, String token, boolean rememberMe) {
    Cookie cookie = new Cookie(SessionAuthenticationFilter.SESSION_COOKIE_NAME, token);
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setSecure(cookieSecure);
    cookie.setMaxAge(rememberMe ? (int) Duration.ofDays(30).getSeconds() : -1);
    cookie.setAttribute("SameSite", "Lax");
    response.addCookie(cookie);
  }

  private void clearSessionCookie(HttpServletResponse response) {
    Cookie cookie = new Cookie(SessionAuthenticationFilter.SESSION_COOKIE_NAME, "");
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    cookie.setAttribute("SameSite", "Lax");
    response.addCookie(cookie);
  }

  private String extractSessionToken(HttpServletRequest request) {
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
