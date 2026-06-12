package com.lawai.api.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawai.api.dto.ChatResponse;
import com.lawai.api.research.dto.LegalResearchRequest;
import com.lawai.api.research.dto.LegalResearchResponse;
import com.lawai.api.research.dto.ResearchProgressEvent;
import com.lawai.api.research.dto.ResearchStepDto;
import com.lawai.api.research.service.LegalResearchService;
import com.lawai.common.client.ActivityLogClient;
import com.lawai.api.service.ChatHistoryService;
import com.lawai.common.i18n.I18nMessages;
import com.lawai.common.model.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/research")
public class LegalResearchController {

  private static final Logger log = LoggerFactory.getLogger(LegalResearchController.class);
  private static final long SSE_TIMEOUT_MS = 180_000L;

  private final LegalResearchService legalResearchService;
  private final ActivityLogClient activityLogClient;
  private final ChatHistoryService chatHistoryService;
  private final ObjectMapper objectMapper;
  private final I18nMessages i18n;

  public LegalResearchController(
      LegalResearchService legalResearchService,
      ActivityLogClient activityLogClient,
      ChatHistoryService chatHistoryService,
      ObjectMapper objectMapper,
      I18nMessages i18n
  ) {
    this.legalResearchService = legalResearchService;
    this.activityLogClient = activityLogClient;
    this.chatHistoryService = chatHistoryService;
    this.objectMapper = objectMapper;
    this.i18n = i18n;
  }

  @PostMapping("/run")
  public LegalResearchResponse run(@Valid @RequestBody LegalResearchRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    log.info("Hukuki arastirma istegi: user={}, query={}", user.email(), request.query());
    LegalResearchResponse response = finalizeResponse(user, request, legalResearchService.run(request.query()));
    logActivity(user, request.query());
    return response;
  }

  @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter runStream(@Valid @RequestBody LegalResearchRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    SecurityContext securityContext = SecurityContextHolder.getContext();
    Locale locale = LocaleContextHolder.getLocale();
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

    Runnable streamTask = () -> {
      LocaleContextHolder.setLocaleContext(new SimpleLocaleContext(locale));
      try {
        LegalResearchResponse response = legalResearchService.run(request.query(), step -> sendStep(emitter, step));
        LegalResearchResponse finalized = finalizeResponse(user, request, response);
        sendEvent(emitter, "complete", ResearchProgressEvent.complete(finalized));
        emitter.complete();
        logActivity(user, request.query());
      } catch (Exception exception) {
        log.error("SSE arastirma hatasi: {}", exception.getMessage(), exception);
        try {
          emitter.send(SseEmitter.event().name("error").data(Map.of("detail", i18n.detail(exception.getMessage()))));
        } catch (IOException ignored) {
          // emitter already broken
        }
        emitter.completeWithError(exception);
      } finally {
        LocaleContextHolder.resetLocaleContext();
      }
    };

    CompletableFuture.runAsync(new DelegatingSecurityContextRunnable(streamTask, securityContext));
    emitter.onTimeout(emitter::complete);
    return emitter;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
    return Map.of("detail", i18n.detail(exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleValidation(MethodArgumentNotValidException exception) {
    String detail = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getDefaultMessage())
        .filter(message -> message != null && !message.isBlank())
        .findFirst()
        .orElse(i18n.get("error.invalid-request"));
    return Map.of("detail", detail);
  }

  @ExceptionHandler(LegalResearchException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String, String> handleResearchFailure(LegalResearchException exception) {
    log.error("Hukuki arastirma hatasi: {}", exception.getMessage(), exception);
    return Map.of("detail", i18n.get("research.failed", i18n.detail(exception.getMessage())));
  }

  private LegalResearchResponse finalizeResponse(
      AuthenticatedUser user,
      LegalResearchRequest request,
      LegalResearchResponse response
  ) {
    ChatResponse chatResponse = new ChatResponse(
        response.answer(),
        List.of(),
        List.of(),
        response.disclaimer(),
        null
    );
    var session = chatHistoryService.saveExchange(user, request.sessionId(), request.query(), chatResponse);
    return new LegalResearchResponse(
        response.plan(),
        response.steps(),
        response.sourceResults(),
        response.answer(),
        response.disclaimer(),
        session.id()
    );
  }

  private void sendStep(SseEmitter emitter, ResearchStepDto step) {
    sendEvent(emitter, "step", ResearchProgressEvent.step(step.type(), step.label(), step.status()));
  }

  private void sendEvent(SseEmitter emitter, String name, ResearchProgressEvent event) {
    try {
      emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(event)));
    } catch (IOException exception) {
      throw new LegalResearchException("SSE olay gonderilemedi.", exception);
    }
  }

  private void logActivity(AuthenticatedUser user, String query) {
    activityLogClient.logBackend(
        user,
        "legal-research",
        "Asistan",
        "Hukuki arastirma asistani calistirildi: " + query,
        "/api/research/run"
    );
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("error.session-required");
  }
}
