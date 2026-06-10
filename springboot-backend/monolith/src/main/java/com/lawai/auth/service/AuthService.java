package com.lawai.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.auth.dto.AuthChangePasswordRequest;
import com.lawai.auth.dto.AuthForgotPasswordRequest;
import com.lawai.auth.dto.AuthLoginRequest;
import com.lawai.auth.dto.AuthPasswordResetResponse;
import com.lawai.auth.dto.AuthProfileUpdateRequest;
import com.lawai.auth.dto.AuthRegisterRequest;
import com.lawai.auth.dto.AuthRegisterResponse;
import com.lawai.auth.dto.AuthSessionResponse;
import com.lawai.auth.dto.AuthUserDto;
import com.lawai.auth.model.AuthenticatedUser;
import com.lawai.auth.model.EmailVerificationRecord;
import com.lawai.auth.model.PasswordResetRecord;
import com.lawai.auth.model.SessionRecord;
import com.lawai.auth.model.UserRecord;
import com.lawai.persistence.entity.AuthSessionEntity;
import com.lawai.persistence.entity.EmailVerificationEntity;
import com.lawai.persistence.entity.PasswordResetEntity;
import com.lawai.persistence.entity.UserEntity;
import com.lawai.persistence.repository.AuthSessionRepository;
import com.lawai.persistence.repository.EmailVerificationRepository;
import com.lawai.persistence.repository.PasswordResetRepository;
import com.lawai.persistence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

  private static final String PASSWORD_RESET_ACK_MESSAGE =
      "E-posta adresi sistemde bulunuyorsa sifirlama baglantisi gonderildi.";

  private final ObjectMapper objectMapper;
  private final UserRepository userRepository;
  private final AuthSessionRepository authSessionRepository;
  private final PasswordResetRepository passwordResetRepository;
  private final EmailVerificationRepository emailVerificationRepository;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
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
      UserRepository userRepository,
      AuthSessionRepository authSessionRepository,
      PasswordResetRepository passwordResetRepository,
      EmailVerificationRepository emailVerificationRepository,
      EmailService emailService,
      @Value("${app.auth.preview-reset-token:false}") boolean previewResetToken,
      @Value("${app.auth.reset-password-url-base:http://localhost:3000}") String resetPasswordUrlBase,
      @Value("${app.auth.preview-verification-token:false}") boolean previewVerificationToken,
      @Value("${app.auth.verification-url-base:http://localhost:3000}") String verificationUrlBase,
      @Value("${app.auth.bootstrap-email:admin@lawai.local}") String bootstrapEmail,
      @Value("${app.auth.google-client-id:}") String googleClientId
  ) {
    this.objectMapper = objectMapper;
    this.userRepository = userRepository;
    this.authSessionRepository = authSessionRepository;
    this.passwordResetRepository = passwordResetRepository;
    this.emailVerificationRepository = emailVerificationRepository;
    this.emailService = emailService;
    this.previewResetToken = previewResetToken;
    this.resetPasswordUrlBase = resetPasswordUrlBase;
    this.previewVerificationToken = previewVerificationToken;
    this.verificationUrlBase = verificationUrlBase == null ? "" : verificationUrlBase.trim();
    this.bootstrapEmail = normalizeEmail(bootstrapEmail);
    this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
  }

  @Transactional
  public AuthRegisterResponse register(AuthRegisterRequest request) {
    return registerInternal(request, "USER");
  }

  @Transactional
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
    userRepository.save(UserEntity.fromRecord(user));

    String verificationToken = generateToken();
    EmailVerificationRecord verification = new EmailVerificationRecord(
        hash(verificationToken),
        user.id(),
        now.plusHours(48),
        false,
        now
    );
    emailVerificationRepository.deleteByUserIdAndUsedFalse(user.id());
    emailVerificationRepository.save(EmailVerificationEntity.fromRecord(verification));

    String link = buildVerificationLink(verificationToken);
    try {
      emailService.sendVerificationEmail(user.email(), link);
    } catch (Exception ex) {
      System.out.println("[AuthService] Failed to send verification email: " + ex.getMessage());
    }
    return new AuthRegisterResponse(
        "Kullanici olusturuldu; e-posta dogrulamasi gereklidir.",
        previewVerificationToken ? verificationToken : null,
        verification.expiresAt(),
        previewVerificationToken ? link : null
    );
  }

  @Transactional
  public AuthSessionResponse login(AuthLoginRequest request) {
    UserRecord user = findUserByEmail(normalizeEmail(request.email()))
        .orElseThrow(() -> new BadCredentialsException("E-posta veya sifre hatali."));
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new BadCredentialsException("E-posta veya sifre hatali.");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    replaceUser(user.withLastLoginAt(now));
    return new AuthSessionResponse(toDto(user.withLastLoginAt(now)));
  }

  @Transactional
  public AuthUserDto loginWithGoogle(String credential) {
    GoogleTokenPayload googleUser = verifyGoogleCredential(credential);
    Optional<UserRecord> existingUser = findUserByEmail(googleUser.email());
    if (existingUser.isPresent()) {
      UserRecord updatedUser = existingUser.get().withLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
      replaceUser(updatedUser);
      return toDto(updatedUser);
    }
    UserRecord createdUser = createGoogleUser(googleUser);
    userRepository.save(UserEntity.fromRecord(createdUser));
    return toDto(createdUser);
  }

  @Transactional
  public void logout(String sessionToken) {
    if (!StringUtils.hasText(sessionToken)) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    authSessionRepository.deleteByExpiresAtBefore(now);
    for (AuthSessionEntity session : authSessionRepository.findByExpiresAtAfter(now)) {
      if (passwordEncoder.matches(sessionToken, session.getTokenHash())) {
        authSessionRepository.delete(session);
        return;
      }
    }
  }

  @Transactional(readOnly = true)
  public AuthUserDto currentUser(String sessionToken) {
    SessionRecord session = requireSession(sessionToken);
    UserRecord user = requireUser(session.userId());
    return toDto(user);
  }

  @Transactional
  public AuthUserDto updateProfile(String sessionToken, AuthProfileUpdateRequest request) {
    SessionRecord session = requireSession(sessionToken);
    UserRecord user = requireUser(session.userId());
    String nextName = request.name().trim();
    String nextEmail = normalizeEmail(request.email());

    userRepository.findByEmailIgnoreCase(nextEmail)
        .filter(item -> !item.getId().equals(user.id()))
        .ifPresent(item -> {
          throw new IllegalArgumentException("Bu e-posta zaten kullaniliyor.");
        });

    UserRecord updatedUser = user.withProfile(nextName, nextEmail);
    replaceUser(updatedUser);
    return toDto(updatedUser);
  }

  @Transactional
  public AuthPasswordResetResponse requestPasswordReset(AuthForgotPasswordRequest request) {
    String email = normalizeEmail(request.email());
    Optional<UserRecord> userOptional = findUserByEmail(email);
    if (userOptional.isEmpty()) {
      return new AuthPasswordResetResponse(PASSWORD_RESET_ACK_MESSAGE, null, null, null);
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

    passwordResetRepository.deleteByUserIdAndUsedFalse(user.id());
    passwordResetRepository.save(PasswordResetEntity.fromRecord(resetRecord));
    String resetLink = buildResetLink(resetToken);
    try {
      emailService.sendPasswordResetEmail(user.email(), resetLink);
    } catch (Exception ex) {
      System.out.println("[AuthService] Sifre sifirlama e-postasi gonderilemedi: " + ex.getMessage());
    }
    return new AuthPasswordResetResponse(
        PASSWORD_RESET_ACK_MESSAGE,
        previewResetToken ? resetToken : null,
        previewResetToken ? resetRecord.expiresAt() : null,
        previewResetToken ? resetLink : null
    );
  }

  @Transactional
  public AuthUserDto verifyEmail(String token) {
    EmailVerificationRecord verification = requireVerificationToken(token);
    UserRecord user = requireUser(verification.userId());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UserRecord updated = user.withVerified(true, now).withLastLoginAt(user.lastLoginAt());
    replaceUser(updated);

    emailVerificationRepository.findAll().stream()
        .filter(item -> item.getUserId().equals(user.id()) && !item.isUsed())
        .forEach(item -> {
          item.setUsed(true);
          emailVerificationRepository.save(item);
        });
    return toDto(updated);
  }

  @Transactional
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
    emailVerificationRepository.deleteByUserIdAndUsedFalse(user.id());
    emailVerificationRepository.save(EmailVerificationEntity.fromRecord(verification));

    String link = buildVerificationLink(verificationToken);
    try {
      emailService.sendVerificationEmail(user.email(), link);
    } catch (Exception ex) {
      System.out.println("[AuthService] Failed to send verification email: " + ex.getMessage());
    }
    return new AuthRegisterResponse("Dogrulama baglantisi gonderildi.", previewVerificationToken ? verificationToken : null, verification.expiresAt(), previewVerificationToken ? link : null);
  }

  @Transactional
  public AuthUserDto resetPassword(String token, String newPassword) {
    PasswordResetRecord reset = requireResetToken(token);
    UserRecord user = requireUser(reset.userId());
    UserRecord updatedUser = user.withPasswordHash(passwordEncoder.encode(newPassword)).withLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
    replaceUser(updatedUser);
    invalidateSessionsForUser(user.id());
    markResetUsed(reset.userId());
    return toDto(updatedUser);
  }

  @Transactional
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

  @Transactional(readOnly = true)
  public List<AuthUserDto> listUsers(String sessionToken) {
    requireAdmin(sessionToken);
    return userRepository.findAll().stream()
        .map(UserEntity::toRecord)
        .map(this::normalizeUserRole)
        .sorted(Comparator.comparing(UserRecord::createdAt).reversed())
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public AuthUserDto getUser(String sessionToken, String userId) {
    requireAdmin(sessionToken);
    return userRepository.findById(userId)
        .map(UserEntity::toRecord)
        .map(this::normalizeUserRole)
        .map(this::toDto)
        .orElseThrow(() -> new IllegalArgumentException("Kullanici bulunamadi."));
  }

  @Transactional
  public String issueSessionToken(AuthLoginRequest request) {
    UserRecord user = findUserByEmail(normalizeEmail(request.email()))
        .orElseThrow(() -> new BadCredentialsException("E-posta veya sifre hatali."));
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new BadCredentialsException("E-posta veya sifre hatali.");
    }

    UserRecord verifiedUser = requireVerifiedUser(user);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String token = generateToken();
    SessionRecord session = new SessionRecord(
        hash(token),
        verifiedUser.id(),
        now.plusDays(Boolean.TRUE.equals(request.rememberMe()) ? 30 : 7),
        now
    );
    replaceUser(verifiedUser.withLastLoginAt(now));
    addSession(session);
    return token;
  }

  @Transactional
  public String issueSessionTokenForUser(String userId, boolean rememberMe) {
    UserRecord user = requireVerifiedUser(requireUser(userId));
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

  @Transactional(readOnly = true)
  public AuthenticatedUser requireAuthenticatedUser(String sessionToken) {
    SessionRecord session = requireSession(sessionToken);
    UserRecord user = requireUser(session.userId());
    return new AuthenticatedUser(user.id(), user.name(), user.email(), effectiveRole(user));
  }

  @Transactional(readOnly = true)
  public boolean hasUsers() {
    return userRepository.count() > 0;
  }

  private UserRecord requireVerifiedUser(UserRecord user) {
    if (Boolean.TRUE.equals(user.verified())) {
      return user;
    }
    if (previewVerificationToken) {
      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      UserRecord verifiedUser = user.withVerified(true, now);
      replaceUser(verifiedUser);
      return verifiedUser;
    }
    throw new BadCredentialsException("E-posta adresi dogrulanmadi. Lutfen e-posta onayinizi gerceklestirin.");
  }

  private void requireAdmin(String sessionToken) {
    AuthUserDto user = currentUser(sessionToken);
    if (!"ADMIN".equalsIgnoreCase(user.role())) {
      throw new AccessDeniedException("Yonetici yetkisi gerekli.");
    }
  }

  private AuthUserDto toDto(UserRecord user) {
    return new AuthUserDto(user.id(), user.name(), user.email(), effectiveRole(user), user.createdAt(), user.lastLoginAt());
  }

  private UserRecord requireUser(String userId) {
    return userRepository.findById(userId)
        .map(UserEntity::toRecord)
        .map(this::normalizeUserRole)
        .orElseThrow(() -> new BadCredentialsException("Oturum gecersiz."));
  }

  private SessionRecord requireSession(String sessionToken) {
    if (!StringUtils.hasText(sessionToken)) {
      throw new BadCredentialsException("Oturum bulunamadi.");
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    authSessionRepository.deleteByExpiresAtBefore(now);
    SessionRecord found = null;
    for (AuthSessionEntity session : authSessionRepository.findByExpiresAtAfter(now)) {
      if (passwordEncoder.matches(sessionToken, session.getTokenHash())) {
        found = session.toRecord();
        break;
      }
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
    PasswordResetRecord found = null;
    for (PasswordResetEntity reset : passwordResetRepository.findAll()) {
      if (reset.getExpiresAt().isBefore(now) || reset.isUsed()) {
        continue;
      }
      if (passwordEncoder.matches(token, reset.getTokenHash())) {
        found = reset.toRecord();
        break;
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("Sifirlama tokeni gecersiz veya suresi dolmus.");
    }
    return found;
  }

  private EmailVerificationRecord requireVerificationToken(String token) {
    if (!StringUtils.hasText(token)) {
      throw new IllegalArgumentException("Dogrulama tokeni gerekli.");
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    EmailVerificationRecord found = null;
    for (EmailVerificationEntity rec : emailVerificationRepository.findAll()) {
      if (rec.getExpiresAt().isBefore(now) || rec.isUsed()) {
        continue;
      }
      if (passwordEncoder.matches(token, rec.getTokenHash())) {
        found = rec.toRecord();
        break;
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("Dogrulama tokeni gecersiz veya suresi dolmus.");
    }
    return found;
  }

  private void replaceUser(UserRecord updatedUser) {
    UserEntity entity = userRepository.findById(updatedUser.id())
        .orElseGet(() -> UserEntity.fromRecord(updatedUser));
    entity.applyRecord(updatedUser);
    userRepository.save(entity);
  }

  private void addSession(SessionRecord session) {
    authSessionRepository.save(AuthSessionEntity.fromRecord(session));
  }

  private void invalidateSessionsForUser(String userId) {
    authSessionRepository.deleteByUserId(userId);
  }

  private void markResetUsed(String userId) {
    passwordResetRepository.findByUserIdAndUsedFalse(userId).forEach(entity -> {
      entity.setUsed(true);
      passwordResetRepository.save(entity);
    });
  }

  private Optional<UserRecord> findUserByEmail(String email) {
    return userRepository.findByEmailIgnoreCase(email)
        .map(UserEntity::toRecord)
        .map(this::normalizeUserRole);
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

  private UserRecord normalizeUserRole(UserRecord user) {
    String role = StringUtils.hasText(user.role())
        ? user.role().trim().toUpperCase()
        : (user.email().equalsIgnoreCase(bootstrapEmail) ? "ADMIN" : "USER");
    UserRecord withRole = StringUtils.hasText(user.role()) && role.equals(user.role().trim().toUpperCase())
        ? user
        : user.withRole(role);
    if (withRole.verified() == null) {
      return withRole.withVerified(Boolean.FALSE, null);
    }
    return withRole;
  }

  private record GoogleTokenPayload(String email, String name) {
  }
}
