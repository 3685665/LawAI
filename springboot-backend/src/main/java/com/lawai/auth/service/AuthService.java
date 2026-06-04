package com.lawai.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.auth.dto.AuthChangePasswordRequest;
import com.lawai.auth.dto.AuthForgotPasswordRequest;
import com.lawai.auth.dto.AuthLoginRequest;
import com.lawai.auth.dto.AuthPasswordResetResponse;
import com.lawai.auth.dto.AuthProfileUpdateRequest;
import com.lawai.auth.dto.AuthRegisterRequest;
import com.lawai.auth.dto.AuthSessionResponse;
import com.lawai.auth.dto.AuthUserDto;
import com.lawai.auth.model.AuthStorePayload;
import com.lawai.auth.model.AuthenticatedUser;
import com.lawai.auth.model.PasswordResetRecord;
import com.lawai.auth.model.SessionRecord;
import com.lawai.auth.model.UserRecord;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

  private final ObjectMapper objectMapper;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final Path storagePath;
  private final boolean previewResetToken;
  private final String resetPasswordUrlBase;
  private final String bootstrapEmail;

  public AuthService(
      ObjectMapper objectMapper,
      @Value("${app.auth.preview-reset-token:true}") boolean previewResetToken,
      @Value("${app.auth.reset-password-url-base:http://localhost:3000}") String resetPasswordUrlBase,
      @Value("${app.auth.bootstrap-email:admin@lawai.local}") String bootstrapEmail
  ) {
    this.objectMapper = objectMapper;
    this.storagePath = Path.of(System.getProperty("user.dir"), "data", "auth-store.json");
    this.previewResetToken = previewResetToken;
    this.resetPasswordUrlBase = resetPasswordUrlBase;
    this.bootstrapEmail = normalizeEmail(bootstrapEmail);
  }

  public AuthSessionResponse register(AuthRegisterRequest request) {
    return registerInternal(request, "USER");
  }

  public AuthSessionResponse registerAdmin(AuthRegisterRequest request) {
    return registerInternal(request, "ADMIN");
  }

  private AuthSessionResponse registerInternal(AuthRegisterRequest request, String role) {
    String email = normalizeEmail(request.email());
    if (findUserByEmail(email).isPresent()) {
      throw new IllegalArgumentException("Bu e-posta zaten kullaniliyor.");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UserRecord user = new UserRecord(
        UUID.randomUUID().toString(),
        request.name().trim(),
        email,
        passwordEncoder.encode(request.password()),
        role == null ? "USER" : role.trim().toUpperCase(),
        now,
        now
    );
    AuthStorePayload payload = load();
    List<UserRecord> users = new ArrayList<>(safeList(payload.users()));
    users.add(user);
    save(new AuthStorePayload(users, safeList(payload.sessions()), safeList(payload.passwordResets())));
    return new AuthSessionResponse(toDto(user));
  }

  public AuthSessionResponse login(AuthLoginRequest request) {
    UserRecord user = findUserByEmail(normalizeEmail(request.email()))
        .orElseThrow(() -> new BadCredentialsException("E-posta veya sifre hatali."));
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new BadCredentialsException("E-posta veya sifre hatali.");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UserRecord updatedUser = user.withLastLoginAt(now);
    replaceUser(updatedUser);
    return new AuthSessionResponse(toDto(updatedUser));
  }

  public void logout(String sessionToken) {
    if (!StringUtils.hasText(sessionToken)) {
      return;
    }
    AuthStorePayload payload = load();
    List<SessionRecord> sessions = safeList(payload.sessions()).stream()
        .filter(item -> !passwordEncoder.matches(sessionToken, item.tokenHash()))
        .toList();
    save(new AuthStorePayload(safeList(payload.users()), sessions, safeList(payload.passwordResets())));
  }

  public AuthUserDto currentUser(String sessionToken) {
    SessionRecord session = requireSession(sessionToken);
    UserRecord user = requireUser(session.userId());
    return toDto(user);
  }

  public AuthUserDto updateProfile(String sessionToken, AuthProfileUpdateRequest request) {
    SessionRecord session = requireSession(sessionToken);
    UserRecord user = requireUser(session.userId());
    String nextName = request.name().trim();
    String nextEmail = normalizeEmail(request.email());

    safeList(load().users()).stream()
        .filter(item -> !item.id().equals(user.id()))
        .filter(item -> item.email().equalsIgnoreCase(nextEmail))
        .findFirst()
        .ifPresent(item -> {
          throw new IllegalArgumentException("Bu e-posta zaten kullaniliyor.");
        });

    UserRecord updatedUser = user.withProfile(nextName, nextEmail);
    replaceUser(updatedUser);
    return toDto(updatedUser);
  }

  public AuthPasswordResetResponse requestPasswordReset(AuthForgotPasswordRequest request) {
    String email = normalizeEmail(request.email());
    Optional<UserRecord> userOptional = findUserByEmail(email);
    if (userOptional.isEmpty()) {
      return new AuthPasswordResetResponse("E-posta adresi sistemde bulunuyorsa sifirlama baglantisi olusturuldu.", null, null, null);
    }

    UserRecord user = userOptional.get();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String resetToken = generateToken();
    PasswordResetRecord resetRecord = new PasswordResetRecord(
        hash(resetToken),
        user.id(),
        now.plusHours(2),
        false,
        now
    );

    AuthStorePayload payload = load();
    List<PasswordResetRecord> resets = new ArrayList<>(safeList(payload.passwordResets()));
    resets.removeIf(item -> item.userId().equals(user.id()) && !item.used());
    resets.add(resetRecord);
    save(new AuthStorePayload(safeList(payload.users()), safeList(payload.sessions()), resets));
    String resetLink = buildResetLink(resetToken);
    return new AuthPasswordResetResponse(
        "Sifre sifirlama baglantisi olusturuldu. Baglanti e-posta ile iletilir.",
        previewResetToken ? resetToken : null,
        resetRecord.expiresAt(),
        previewResetToken ? resetLink : null
    );
  }

  public AuthUserDto resetPassword(String token, String newPassword) {
    PasswordResetRecord reset = requireResetToken(token);
    UserRecord user = requireUser(reset.userId());
    UserRecord updatedUser = user.withPasswordHash(passwordEncoder.encode(newPassword)).withLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
    replaceUser(updatedUser);
    invalidateSessionsForUser(user.id());
    markResetUsed(reset.userId());
    return toDto(updatedUser);
  }

  public AuthUserDto changePassword(String sessionToken, AuthChangePasswordRequest request) {
    SessionRecord session = requireSession(sessionToken);
    UserRecord user = requireUser(session.userId());
    if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
      throw new BadCredentialsException("Mevcut sifre hatali.");
    }
    UserRecord updatedUser = user.withPasswordHash(passwordEncoder.encode(request.newPassword()));
    replaceUser(updatedUser);
    invalidateSessionsForUser(user.id());
    return toDto(updatedUser);
  }

  public List<AuthUserDto> listUsers(String sessionToken) {
    requireAdmin(sessionToken);
    return safeList(load().users()).stream()
        .sorted(Comparator.comparing(UserRecord::createdAt).reversed())
        .map(this::toDto)
        .toList();
  }

  public AuthUserDto getUser(String sessionToken, String userId) {
    requireAdmin(sessionToken);
    return safeList(load().users()).stream()
        .filter(item -> item.id().equals(userId))
        .findFirst()
        .map(this::toDto)
        .orElseThrow(() -> new IllegalArgumentException("Kullanici bulunamadi."));
  }

  public String issueSessionToken(AuthLoginRequest request) {
    UserRecord user = findUserByEmail(normalizeEmail(request.email()))
        .orElseThrow(() -> new BadCredentialsException("E-posta veya sifre hatali."));
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new BadCredentialsException("E-posta veya sifre hatali.");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String token = generateToken();
    SessionRecord session = new SessionRecord(
        hash(token),
        user.id(),
        now.plusDays(Boolean.TRUE.equals(request.rememberMe()) ? 30 : 7),
        now
    );
    replaceUser(user.withLastLoginAt(now));
    addSession(session);
    return token;
  }

  public AuthenticatedUser requireAuthenticatedUser(String sessionToken) {
    SessionRecord session = requireSession(sessionToken);
    UserRecord user = requireUser(session.userId());
    return new AuthenticatedUser(user.id(), user.name(), user.email(), effectiveRole(user));
  }

  private void requireAdmin(String sessionToken) {
    AuthUserDto user = currentUser(sessionToken);
    if (!"ADMIN".equalsIgnoreCase(user.role())) {
      throw new org.springframework.security.access.AccessDeniedException("Yonetici yetkisi gerekli.");
    }
  }

  public boolean hasUsers() {
    return !safeList(load().users()).isEmpty();
  }

  private AuthUserDto toDto(UserRecord user) {
    return new AuthUserDto(user.id(), user.name(), user.email(), effectiveRole(user), user.createdAt(), user.lastLoginAt());
  }

  private UserRecord requireUser(String userId) {
    return safeList(load().users()).stream()
        .filter(item -> item.id().equals(userId))
        .findFirst()
        .orElseThrow(() -> new BadCredentialsException("Oturum gecersiz."));
  }

  private SessionRecord requireSession(String sessionToken) {
    if (!StringUtils.hasText(sessionToken)) {
      throw new BadCredentialsException("Oturum bulunamadi.");
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    AuthStorePayload payload = load();
    List<SessionRecord> active = new ArrayList<>();
    SessionRecord found = null;
    for (SessionRecord session : safeList(payload.sessions())) {
      if (session.expiresAt().isBefore(now)) {
        continue;
      }
      active.add(session);
      if (passwordEncoder.matches(sessionToken, session.tokenHash())) {
        found = session;
      }
    }
    if (active.size() != safeList(payload.sessions()).size()) {
      save(new AuthStorePayload(safeList(payload.users()), active, safeList(payload.passwordResets())));
    }
    if (found == null) {
      throw new BadCredentialsException("Oturum gecersiz veya suresi dolmus.");
    }
    return found;
  }

  private PasswordResetRecord requireResetToken(String token) {
    if (!StringUtils.hasText(token)) {
      throw new IllegalArgumentException("Sifirlama tokeni gerekli.");
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    AuthStorePayload payload = load();
    List<PasswordResetRecord> nextResets = new ArrayList<>();
    PasswordResetRecord found = null;
    for (PasswordResetRecord reset : safeList(payload.passwordResets())) {
      if (reset.expiresAt().isBefore(now) || reset.used()) {
        nextResets.add(reset);
        continue;
      }
      nextResets.add(reset);
      if (passwordEncoder.matches(token, reset.tokenHash())) {
        found = reset;
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("Sifirlama tokeni gecersiz veya suresi dolmus.");
    }
    return found;
  }

  private void replaceUser(UserRecord updatedUser) {
    AuthStorePayload payload = load();
    List<UserRecord> users = safeList(payload.users()).stream()
        .map(item -> item.id().equals(updatedUser.id()) ? updatedUser : item)
        .toList();
    save(new AuthStorePayload(users, safeList(payload.sessions()), safeList(payload.passwordResets())));
  }

  private void addSession(SessionRecord session) {
    AuthStorePayload payload = load();
    List<SessionRecord> sessions = new ArrayList<>(safeList(payload.sessions()));
    sessions.add(session);
    save(new AuthStorePayload(safeList(payload.users()), sessions, safeList(payload.passwordResets())));
  }

  private void invalidateSessionsForUser(String userId) {
    AuthStorePayload payload = load();
    List<SessionRecord> sessions = safeList(payload.sessions()).stream()
        .filter(item -> !item.userId().equals(userId))
        .toList();
    save(new AuthStorePayload(safeList(payload.users()), sessions, safeList(payload.passwordResets())));
  }

  private void markResetUsed(String userId) {
    AuthStorePayload payload = load();
    List<PasswordResetRecord> resets = safeList(payload.passwordResets()).stream()
        .map(item -> item.userId().equals(userId) && !item.used() ? item.withUsed(true) : item)
        .toList();
    save(new AuthStorePayload(safeList(payload.users()), safeList(payload.sessions()), resets));
  }

  private Optional<UserRecord> findUserByEmail(String email) {
    return safeList(load().users()).stream()
        .filter(item -> item.email().equalsIgnoreCase(email))
        .findFirst();
  }

  private String effectiveRole(UserRecord user) {
    if (StringUtils.hasText(user.role())) {
      return user.role().trim().toUpperCase();
    }
    return user.email().equalsIgnoreCase(bootstrapEmail) ? "ADMIN" : "USER";
  }

  private AuthStorePayload load() {
    if (!Files.exists(storagePath)) {
      return new AuthStorePayload(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
    try {
      AuthStorePayload payload = objectMapper.readValue(Files.readString(storagePath), AuthStorePayload.class);
      List<UserRecord> users = safeList(payload.users()).stream()
          .map(this::normalizeUserRole)
          .toList();
      return new AuthStorePayload(users, safeList(payload.sessions()), safeList(payload.passwordResets()));
    } catch (IOException exception) {
      throw new IllegalStateException("Auth verisi yuklenemedi: " + exception.getMessage(), exception);
    }
  }

  private void save(AuthStorePayload payload) {
    try {
      Files.createDirectories(storagePath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), payload);
    } catch (IOException exception) {
      throw new IllegalStateException("Auth verisi kaydedilemedi: " + exception.getMessage(), exception);
    }
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  private String generateToken() {
    return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
  }

  private String hash(String value) {
    return passwordEncoder.encode(value);
  }

  private String buildResetLink(String token) {
    String baseUrl = resetPasswordUrlBase == null ? "" : resetPasswordUrlBase.trim().replaceAll("/+$", "");
    return baseUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
  }

  private <T> List<T> safeList(List<T> items) {
    return items == null ? List.of() : new ArrayList<>(items);
  }

  private UserRecord normalizeUserRole(UserRecord user) {
    String role = StringUtils.hasText(user.role())
        ? user.role().trim().toUpperCase()
        : (user.email().equalsIgnoreCase(bootstrapEmail) ? "ADMIN" : "USER");
    return StringUtils.hasText(user.role()) && role.equals(user.role().trim().toUpperCase())
        ? user
        : user.withRole(role);
  }
}


