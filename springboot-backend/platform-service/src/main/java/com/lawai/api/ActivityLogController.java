package com.lawai.api;

import com.lawai.api.dto.ActivityLogCreateRequest;
import com.lawai.api.dto.ActivityLogDto;
import com.lawai.api.service.ActivityLogService;
import com.lawai.common.model.AuthenticatedUser;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activity-logs")
public class ActivityLogController {

  private final ActivityLogService activityLogService;

  public ActivityLogController(ActivityLogService activityLogService) {
    this.activityLogService = activityLogService;
  }

  @PostMapping
  public ActivityLogDto create(@RequestBody ActivityLogCreateRequest request, Authentication authentication) {
    return activityLogService.logFrontend(requireUser(authentication), request);
  }

  @GetMapping("/me")
  public List<ActivityLogDto> listMine(Authentication authentication) {
    return activityLogService.listForUser(requireUser(authentication));
  }

  @GetMapping
  public List<ActivityLogDto> listAll(Authentication authentication) {
    return activityLogService.listAll(requireUser(authentication));
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("error.session-required");
  }
}
