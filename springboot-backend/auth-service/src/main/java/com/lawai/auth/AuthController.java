package com.lawai.auth;

import com.lawai.common.client.ActivityLogClient;
import com.lawai.auth.dto.AuthChangePasswordRequest;
import com.lawai.auth.dto.AuthForgotPasswordRequest;
import com.lawai.auth.dto.AuthGoogleRequest;
import com.lawai.auth.dto.AuthLoginRequest;
import com.lawai.auth.dto.AuthPasswordResetResponse;
import com.lawai.auth.dto.AuthProfileUpdateRequest;
import com.lawai.auth.dto.AuthRegisterRequest;
import com.lawai.auth.dto.AuthSessionResponse;
import com.lawai.auth.dto.AuthUserDto;
import com.lawai.auth.model.AuthenticatedUser;
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
  private final ActivityLogClient activityLogClient;
  private final boolean cookieSecure;

  public AuthController(AuthService authService, ActivityLogClient activityLogClient, @Value("${app.auth.cookie-secure:false}") boolean cookieSecure) {
    this.authService = authService;
    this.activityLogClient = activityLogClient;
    this.cookieSecure = cookieSecure;
  }

  @PostMapping("/register")
  public com.lawai.auth.dto.AuthRegisterResponse register(@Valid @RequestBody AuthRegisterRequest request, HttpServletResponse response) {
    com.lawai.auth.dto.AuthRegisterResponse result = authService.register(request);
    // Do not auto-login. Require email verification first.
    activityLogClient.logBackend(toCommonUser(new AuthenticatedUser("", request.name(), request.email(), "USER")), "auth-register", "Oturum", "Yeni kullanici kaydi yapildi (dogrulama bekleniyor).", "/api/auth/register");
    return result;
  }

  @GetMapping("/verify")
  public AuthUserDto verify(HttpServletRequest req) {
    String token = req.getParameter("token");
    AuthUserDto user = authService.verifyEmail(token);
    activityLogClient.logBackend(toCommonUser(toAuthenticatedUser(user)), "auth-verify", "Oturum", "E-posta dogrulamasi gerceklesti.", "/api/auth/verify");
    return user;
  }

  @PostMapping("/verify/resend")
  public com.lawai.auth.dto.AuthRegisterResponse resendVerification(@Valid @RequestBody com.lawai.auth.dto.AuthForgotPasswordRequest request) {
    com.lawai.auth.dto.AuthRegisterResponse result = authService.resendVerification(request.email());
    activityLogClient.logBackend(toCommonUser(new AuthenticatedUser("", "", request.email(), "USER")), "auth-verify-resend", "Oturum", "Dogrulama baglantisi yeniden istendi.", "/api/auth/verify/resend");
    return result;
  }

  @PostMapping("/login")
  public AuthSessionResponse login(@Valid @RequestBody AuthLoginRequest request, HttpServletResponse response) {
    authService.login(request);
    String token = authService.issueSessionToken(request);
    setSessionCookie(response, token, Boolean.TRUE.equals(request.rememberMe()));
    AuthUserDto user = authService.currentUser(token);
    activityLogClient.logBackend(toCommonUser(toAuthenticatedUser(user)), "auth-login", "Oturum", "Kullanici giris yapti.", "/api/auth/login");
    return new AuthSessionResponse(user);
  }

  @PostMapping("/google")
  public AuthSessionResponse google(@Valid @RequestBody AuthGoogleRequest request, HttpServletResponse response) {
    AuthUserDto user = authService.loginWithGoogle(request.credential());
    String token = authService.issueSessionTokenForUser(user.id(), true);
    setSessionCookie(response, token, true);
    AuthUserDto currentUser = authService.currentUser(token);
    activityLogClient.logBackend(toCommonUser(toAuthenticatedUser(currentUser)), "auth-google", "Oturum", "Google hesabi ile oturum acildi.", "/api/auth/google");
    return new AuthSessionResponse(currentUser);
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
    String token = extractSessionToken(request);
    try {
      AuthUserDto user = authService.currentUser(token);
      activityLogClient.logBackend(toCommonUser(toAuthenticatedUser(user)), "auth-logout", "Oturum", "Kullanici cikis yapti.", "/api/auth/logout");
    } catch (RuntimeException ignored) {
      // Logout should still clear invalid or expired cookies.
    }
    authService.logout(token);
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
    AuthUserDto user = authService.updateProfile(extractSessionToken(httpRequest), request);
    activityLogClient.logBackend(toCommonUser(toAuthenticatedUser(user)), "profile-update", "Profil", "Profil bilgileri guncellendi.", "/api/auth/me");
    return user;
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
    AuthUserDto currentUser = authService.currentUser(token);
    activityLogClient.logBackend(toCommonUser(toAuthenticatedUser(currentUser)), "password-reset", "Oturum", "Sifre sifirlandi.", "/api/auth/password/reset");
    return new AuthSessionResponse(currentUser);
  }

  @PostMapping("/password/change")
  public AuthSessionResponse change(@Valid @RequestBody AuthChangePasswordRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
    AuthUserDto user = authService.changePassword(extractSessionToken(httpRequest), request);
    String token = authService.issueSessionToken(new AuthLoginRequest(user.email(), request.newPassword(), true));
    setSessionCookie(response, token, true);
    AuthUserDto currentUser = authService.currentUser(token);
    activityLogClient.logBackend(toCommonUser(toAuthenticatedUser(currentUser)), "password-change", "Ayarlar", "Sifre degistirildi.", "/api/auth/password/change");
    return new AuthSessionResponse(currentUser);
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

  private com.lawai.common.model.AuthenticatedUser toCommonUser(AuthenticatedUser user) {
    return new com.lawai.common.model.AuthenticatedUser(user.id(), user.name(), user.email(), user.role());
  }

  private AuthenticatedUser toAuthenticatedUser(AuthUserDto user) {
    return new AuthenticatedUser(user.id(), user.name(), user.email(), user.role());
  }
}
