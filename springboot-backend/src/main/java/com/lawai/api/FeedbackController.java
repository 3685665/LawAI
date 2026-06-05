package com.lawai.api;

import com.lawai.api.dto.FeedbackCreateRequest;
import com.lawai.api.dto.FeedbackRecordDto;
import com.lawai.api.dto.FeedbackSubmissionResponse;
import com.lawai.api.dto.FeedbackUpdateRequest;
import com.lawai.api.dto.FeedbackStatusUpdateRequest;
import com.lawai.api.service.ActivityLogService;
import com.lawai.api.service.FeedbackService;
import com.lawai.auth.model.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

  private final FeedbackService feedbackService;
  private final ActivityLogService activityLogService;

  public FeedbackController(FeedbackService feedbackService, ActivityLogService activityLogService) {
    this.feedbackService = feedbackService;
    this.activityLogService = activityLogService;
  }

  @GetMapping
  public List<FeedbackRecordDto> list(Authentication authentication) {
    return feedbackService.listVisible(requireUser(authentication));
  }

  @PostMapping
  public FeedbackSubmissionResponse submit(@Valid @RequestBody FeedbackCreateRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    FeedbackRecordDto feedback = feedbackService.submit(user, request);
    activityLogService.logBackend(user, "feedback-create", "Geri Bildirim", "Geri bildirim gonderildi: " + feedback.subject(), "/api/feedback");
    return new FeedbackSubmissionResponse("Geri bildiriminiz alindi.", feedback);
  }

  @PatchMapping("/{id}/status")
  public FeedbackRecordDto updateStatus(
      @PathVariable String id,
      @Valid @RequestBody FeedbackStatusUpdateRequest request,
      Authentication authentication
  ) {
    AuthenticatedUser user = requireUser(authentication);
    FeedbackRecordDto response = feedbackService.updateStatus(user, id, request);
    activityLogService.logBackend(user, "feedback-status-update", "Sikayet Yonetimi", "Geri bildirim durumu guncellendi: " + id, "/api/feedback/" + id + "/status");
    return response;
  }

  @PatchMapping("/{id}")
  public FeedbackRecordDto update(
      @PathVariable String id,
      @Valid @RequestBody FeedbackUpdateRequest request,
      Authentication authentication
  ) {
    AuthenticatedUser user = requireUser(authentication);
    FeedbackRecordDto response = feedbackService.update(user, id, request);
    activityLogService.logBackend(user, "feedback-update", "Sikayet Yonetimi", "Geri bildirim guncellendi: " + id, "/api/feedback/" + id);
    return response;
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    feedbackService.delete(user, id);
    activityLogService.logBackend(user, "feedback-delete", "Sikayet Yonetimi", "Geri bildirim silindi: " + id, "/api/feedback/" + id);
    return ResponseEntity.noContent().build();
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("Oturum gerekli.");
  }
}
