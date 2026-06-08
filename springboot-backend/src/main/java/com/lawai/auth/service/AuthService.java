package com.lawai.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lawai.auth.dto.AuthChangePasswordRequest;
import com.lawai.auth.dto.AuthForgotPasswordRequest;
import com.lawai.auth.dto.AuthLoginRequest;
import com.lawai.auth.dto.AuthPasswordResetResponse;
import com.lawai.auth.dto.AuthProfileUpdateRequest;
import com.lawai.auth.dto.AuthRegisterRequest;
import com.lawai.auth.dto.AuthSessionResponse;
import com.lawai.auth.dto.AuthRegisterResponse;
import com.lawai.auth.dto.AuthUserDto;
import com.lawai.auth.model.AuthStorePayload;
import com.lawai.auth.model.AuthenticatedUser;
import com.lawai.auth.model.PasswordResetRecord;
import com.lawai.auth.model.EmailVerificationRecord;
import com.lawai.auth.model.SessionRecord;
import com.lawai.auth.model.UserRecord;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import com.lawai.auth.service.EmailService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final boolean previewResetToken;
  private final String resetPasswordUrlBase;
  private final boolean previewVerificationToken;
  private final String verificationUrlBase;
  private final EmailService emailService;
  private final String bootstrapEmail;
  private final String googleClientId;

  public AuthService(
      ObjectMapper objectMapper,
      EmailService emailService,
      @Value("${app.auth.preview-reset-token:true}") boolean previewResetToken,
      @Value("${app.auth.reset-password-url-base:http://localhost:3000}") String resetPasswordUrlBase,
      @Value("${app.auth.preview-verification-token:true}") boolean previewVerificationToken,
      @Value("${app.auth.verification-url-base:http://localhost:3000}") String verificationUrlBase,
      @Value("${app.auth.bootstrap-email:admin@lawai.local}") String bootstrapEmail,
      @Value("${app.auth.google-client-id:}") String googleClientId
  ) {
    this.objectMapper = objectMapper;
    this.storagePath = Path.of(System.getProperty("user.dir"), "data", "auth-store.json");
    this.previewResetToken = previewResetToken;
    this.resetPasswordUrlBase = resetPasswordUrlBase;
    this.previewVerificationToken = previewVerificationToken;
    this.verificationUrlBase = verificationUrlBase == null ? "" : verificationUrlBase.trim();
    this.emailService = emailService;
    this.bootstrapEmail = normalizeEmail(bootstrapEmail);
    this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
  }

  public AuthRegisterResponse register(AuthRegisterRequest request) {
    return registerInternal(request, "USER");
  }

  public AuthRegisterResponse registerAdmin(AuthRegisterRequest request) {
    return registerInternal(request, "ADMIN");
  }

  private AuthRegisterResponse registerInternal(AuthRegisterRequest request, String role) {
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
        now,
        false,
        null
    );
    AuthStorePayload payload = load();
    List<UserRecord> users = new ArrayList<>(safeList(payload.users()));
    users.add(user);

    // create verification token
    String verificationToken = generateToken();
    EmailVerificationRecord verification = new EmailVerificationRecord(
        hash(verificationToken),
        user.id(),
        now.plusHours(48),
        false,
        now
    );
    List<EmailVerificationRecord> verifications = new ArrayList<>(safeList(payload.verifications()));
    // Remove previous unused tokens for this user
    verifications.removeIf(item -> item.userId().equals(user.id()) && !item.used());
    verifications.add(verification);

    save(new AuthStorePayload(users, safeList(payload.sessions()), safeList(payload.passwordResets()), verifications));

    String link = buildVerificationLink(verificationToken);
    try {
      emailService.sendVerificationEmail(user.email(), link);
    } catch (Exception ex) {
      // fall back to preview behavior if mail sending fails
      System.out.println("[AuthService] Failed to send verification email: " + ex.getMessage());
    }
    return new AuthRegisterResponse(
        "Kullanici olusturuldu; e-posta dogrulamasi gereklidir.",
        previewVerificationToken ? verificationToken : null,
        verification.expiresAt(),
        previewVerificationToken ? link : null
    );
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

  public AuthUserDto loginWithGoogle(String credential) {
    GoogleTokenPayload googleUser = verifyGoogleCredential(credential);
    Optional<UserRecord> existingUser = findUserByEmail(googleUser.email());
    if (existingUser.isPresent()) {
      UserRecord updatedUser = existingUser.get().withLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
      replaceUser(updatedUser);
      return toDto(updatedUser);
    }
    UserRecord createdUser = createGoogleUser(googleUser);
    addUser(createdUser);
    return toDto(createdUser);
  }

  public void logout(String sessionToken) {
    if (!StringUtils.hasText(sessionToken)) {
      return;
    }
    AuthStorePayload payload = load();
    List<SessionRecord> sessions = safeList(payload.sessions()).stream()
        .filter(item -> !passwordEncoder.matches(sessionToken, item.tokenHash()))
        .toList();
    save(new AuthStorePayload(safeList(payload.users()), sessions, safeList(payload.passwordResets()), safeList(payload.verifications())));
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
    save(new AuthStorePayload(safeList(payload.users()), safeList(payload.sessions()), resets, safeList(payload.verifications())));
    String resetLink = buildResetLink(resetToken);
    return new AuthPasswordResetResponse(
        "Sifre sifirlama baglantisi olusturuldu. Baglanti e-posta ile iletilir.",
        previewResetToken ? resetToken : null,
        resetRecord.expiresAt(),
        previewResetToken ? resetLink : null
    );
  }

  public AuthUserDto verifyEmail(String token) {
    EmailVerificationRecord verification = requireVerificationToken(token);
    UserRecord user = requireUser(verification.userId());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UserRecord updated = user.withVerified(true, now).withLastLoginAt(user.lastLoginAt());
    replaceUser(updated);

    // mark verification used
    AuthStorePayload payload = load();
    List<EmailVerificationRecord> verifications = safeList(payload.verifications()).stream()
        .map(item -> item.userId().equals(user.id()) && !item.used() ? item.withUsed(true) : item)
        .toList();
    save(new AuthStorePayload(safeList(payload.users()), safeList(payload.sessions()), safeList(payload.passwordResets()), verifications));
    return toDto(updated);
  }

  public AuthRegisterResponse resendVerification(String email) {
    String normalized = normalizeEmail(email);
    Optional<UserRecord> userOptional = findUserByEmail(normalized);
    if (userOptional.isEmpty()) {
      return new AuthRegisterResponse("E-posta adresi sistemde bulunuyorsa dogrulama baglantisi olusturuldu.", null, null, null);
    }
    UserRecord user = userOptional.get();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String verificationToken = generateToken();
    EmailVerificationRecord verification = new EmailVerificationRecord(
        hash(verificationToken),
        user.id(),
        now.plusHours(48),
        false,
        now
    );
    AuthStorePayload payload = load();
    List<EmailVerificationRecord> verifications = new ArrayList<>(safeList(payload.verifications()));
    verifications.removeIf(item -> item.userId().equals(user.id()) && !item.used());
    verifications.add(verification);
    save(new AuthStorePayload(safeList(payload.users()), safeList(payload.sessions()), safeList(payload.passwordResets()), verifications));

    String link = buildVerificationLink(verificationToken);
    try {
      emailService.sendVerificationEmail(user.email(), link);
    } catch (Exception ex) {
      System.out.println("[AuthService] Failed to send verification email: " + ex.getMessage());
    }
    return new AuthRegisterResponse("Dogrulama baglantisi gonderildi.", previewVerificationToken ? verificationToken : null, verification.expiresAt(), previewVerificationToken ? link : null);
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

    if (!Boolean.TRUE.equals(user.verified())) {
      throw new BadCredentialsException("E-posta adresi dogrulanmadi. Lütfen e-posta onayinizi gerceklestirin.");
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

  public String issueSessionTokenForUser(String userId, boolean rememberMe) {
    UserRecord user = requireUser(userId);
    if (!Boolean.TRUE.equals(user.verified())) {
      throw new BadCredentialsException("E-posta adresi dogrulanmadi. Lütfen e-posta onayinizi gerceklestirin.");
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String token = generateToken();
    SessionRecord session = new SessionRecord(
        hash(token),
        user.id(),
        now.plusDays(rememberMe ? 30 : 7),
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
      save(new AuthStorePayload(safeList(payload.users()), active, safeList(payload.passwordResets()), safeList(payload.verifications())));
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
    save(new AuthStorePayload(users, safeList(payload.sessions()), safeList(payload.passwordResets()), safeList(payload.verifications())));
  }

  private void addUser(UserRecord user) {
    AuthStorePayload payload = load();
    List<UserRecord> users = new ArrayList<>(safeList(payload.users()));
    users.add(user);
    save(new AuthStorePayload(users, safeList(payload.sessions()), safeList(payload.passwordResets()), safeList(payload.verifications())));
  }

  private void addSession(SessionRecord session) {
    AuthStorePayload payload = load();
    List<SessionRecord> sessions = new ArrayList<>(safeList(payload.sessions()));
    sessions.add(session);
    save(new AuthStorePayload(safeList(payload.users()), sessions, safeList(payload.passwordResets()), safeList(payload.verifications())));
  }

  private void invalidateSessionsForUser(String userId) {
    AuthStorePayload payload = load();
    List<SessionRecord> sessions = safeList(payload.sessions()).stream()
        .filter(item -> !item.userId().equals(userId))
        .toList();
    save(new AuthStorePayload(safeList(payload.users()), sessions, safeList(payload.passwordResets()), safeList(payload.verifications())));
  }

  private void markResetUsed(String userId) {
    AuthStorePayload payload = load();
    List<PasswordResetRecord> resets = safeList(payload.passwordResets()).stream()
        .map(item -> item.userId().equals(userId) && !item.used() ? item.withUsed(true) : item)
        .toList();
    save(new AuthStorePayload(safeList(payload.users()), safeList(payload.sessions()), resets, safeList(payload.verifications())));
  }

  private Optional<UserRecord> findUserByEmail(String email) {
    return safeList(load().users()).stream()
        .filter(item -> item.email().equalsIgnoreCase(email))
        .findFirst();
  }

  private UserRecord createGoogleUser(GoogleTokenPayload googleUser) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return new UserRecord(
        UUID.randomUUID().toString(),
        StringUtils.hasText(googleUser.name()) ? googleUser.name().trim() : googleUser.email(),
        googleUser.email(),
        passwordEncoder.encode(generateToken()),
        "USER",
        now,
        now,
        true,
        now
    );
  }

  private GoogleTokenPayload verifyGoogleCredential(String credential) {
    if (!StringUtils.hasText(googleClientId)) {
      throw new IllegalStateException("Google girisi icin GOOGLE_CLIENT_ID ayarlanmadi.");
    }
    if (!StringUtils.hasText(credential)) {
      throw new IllegalArgumentException("Google kimlik bilgisi gerekli.");
    }
    try {
      String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + URLEncoder.encode(credential, StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new BadCredentialsException("Google oturumu dogrulanamadi.");
      }
      JsonNode body = objectMapper.readTree(response.body());
      String audience = textValue(body, "aud");
      String email = normalizeEmail(textValue(body, "email"));
      boolean emailVerified = "true".equalsIgnoreCase(textValue(body, "email_verified"));
      if (!googleClientId.equals(audience)) {
        throw new BadCredentialsException("Google istemci bilgisi gecersiz.");
      }
      if (!emailVerified || !StringUtils.hasText(email)) {
        throw new BadCredentialsException("Google e-posta adresi dogrulanmadi.");
      }
      return new GoogleTokenPayload(email, textValue(body, "name"));
    } catch (IOException exception) {
      throw new IllegalStateException("Google oturumu okunamadi: " + exception.getMessage(), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Google oturumu kesintiye ugradi.", exception);
    }
  }

  private String textValue(JsonNode node, String fieldName) {
    JsonNode value = node == null ? null : node.get(fieldName);
    return value == null || value.isNull() ? "" : value.asText("");
  }

  private String effectiveRole(UserRecord user) {
    if (StringUtils.hasText(user.role())) {
      return user.role().trim().toUpperCase();
    }
    return user.email().equalsIgnoreCase(bootstrapEmail) ? "ADMIN" : "USER";
  }

  private AuthStorePayload load() {
    if (!Files.exists(storagePath)) {
      return new AuthStorePayload(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
    try {
      AuthStorePayload payload = objectMapper.readValue(Files.readString(storagePath), AuthStorePayload.class);
      List<UserRecord> users = safeList(payload.users()).stream()
          .map(this::normalizeUserRole)
          .toList();
      return new AuthStorePayload(users, safeList(payload.sessions()), safeList(payload.passwordResets()), safeList(payload.verifications()));
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

  private String buildVerificationLink(String token) {
    String baseUrl = verificationUrlBase == null ? "" : verificationUrlBase.trim().replaceAll("/+$", "");
    return baseUrl + "/verify?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
  }

  private EmailVerificationRecord requireVerificationToken(String token) {
    if (!StringUtils.hasText(token)) {
      throw new IllegalArgumentException("Dogrulama tokeni gerekli.");
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    AuthStorePayload payload = load();
    List<EmailVerificationRecord> next = new ArrayList<>();
    EmailVerificationRecord found = null;
    for (EmailVerificationRecord rec : safeList(payload.verifications())) {
      if (rec.expiresAt().isBefore(now) || rec.used()) {
        next.add(rec);
        continue;
      }
      next.add(rec);
      if (passwordEncoder.matches(token, rec.tokenHash())) {
        found = rec;
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("Dogrulama tokeni gecersiz veya suresi dolmus.");
    }
    return found;
  }

  private <T> List<T> safeList(List<T> items) {
    return items == null ? List.of() : new ArrayList<>(items);
  }

  private UserRecord normalizeUserRole(UserRecord user) {
    String role = StringUtils.hasText(user.role())
        ? user.role().trim().toUpperCase()
        : (user.email().equalsIgnoreCase(bootstrapEmail) ? "ADMIN" : "USER");
    UserRecord withRole = StringUtils.hasText(user.role()) && role.equals(user.role().trim().toUpperCase())
        ? user
        : user.withRole(role);
    // ensure verified is not null for legacy records
    if (withRole.verified() == null) {
      return withRole.withVerified(Boolean.FALSE, null);
    }
    return withRole;
  }

  private record GoogleTokenPayload(String email, String name) {
  }
}


