package com.lawai.api;

import com.lawai.api.dto.ActivityLogCreateRequest;
import com.lawai.api.service.ActivityLogService;
import com.lawai.common.model.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/activity-logs")
public class InternalActivityLogController {

  private final ActivityLogService activityLogService;

  public InternalActivityLogController(ActivityLogService activityLogService) {
    this.activityLogService = activityLogService;
  }

  @PostMapping
  public ResponseEntity<Void> log(
      @RequestHeader("X-User-Id") String userId,
      @RequestHeader("X-User-Name") String userName,
      @RequestHeader("X-User-Email") String userEmail,
      @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
      @RequestBody Map<String, String> payload
  ) {
    AuthenticatedUser user = new AuthenticatedUser(userId, userName, userEmail, userRole);
    if ("frontend".equals(payload.get("source"))) {
      activityLogService.logFrontend(user, new ActivityLogCreateRequest(
          payload.getOrDefault("action", ""),
          payload.getOrDefault("screen", ""),
          payload.getOrDefault("detail", ""),
          payload.getOrDefault("path", "")
      ));
    } else {
      activityLogService.logBackend(
          user,
          payload.getOrDefault("action", ""),
          payload.getOrDefault("screen", ""),
          payload.getOrDefault("detail", ""),
          payload.getOrDefault("path", "")
      );
    }
    return ResponseEntity.ok().build();
  }
}
