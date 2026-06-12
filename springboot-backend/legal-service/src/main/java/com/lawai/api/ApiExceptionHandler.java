package com.lawai.api;

import com.lawai.common.i18n.I18nMessages;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

  private final I18nMessages i18n;

  public ApiExceptionHandler(I18nMessages i18n) {
    this.i18n = i18n;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("detail", i18n.detail(exception.getMessage())));
  }

  @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
  public ResponseEntity<Map<String, String>> unauthorized(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", i18n.detail(exception.getMessage())));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, String>> forbidden(AccessDeniedException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", i18n.get("error.access-denied")));
  }

  @ExceptionHandler({RestClientException.class, IllegalStateException.class})
  public ResponseEntity<Map<String, String>> badGateway(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("detail", i18n.detail(exception.getMessage())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
    String detail = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getDefaultMessage())
        .filter(message -> message != null && !message.isBlank())
        .findFirst()
        .orElse(i18n.get("error.invalid-request"));
    return ResponseEntity.badRequest().body(Map.of("detail", detail));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException exception) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("detail", i18n.get("error.upload-too-large")));
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<Map<String, String>> multipartError(MultipartException exception) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("detail", i18n.get("error.multipart-failed")));
  }
}
