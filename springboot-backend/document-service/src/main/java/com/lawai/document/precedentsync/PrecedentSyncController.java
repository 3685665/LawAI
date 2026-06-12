package com.lawai.document.precedentsync;

import com.lawai.common.model.AuthenticatedUser;
import com.lawai.document.precedentsync.dto.PrecedentSyncRunDto;
import com.lawai.document.precedentsync.dto.PrecedentSyncTaskDto;
import com.lawai.document.precedentsync.dto.PrecedentSyncTaskRequest;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/precedent-sync")
public class PrecedentSyncController {

  private final PrecedentSyncService precedentSyncService;

  public PrecedentSyncController(PrecedentSyncService precedentSyncService) {
    this.precedentSyncService = precedentSyncService;
  }

  @GetMapping("/tasks")
  public List<PrecedentSyncTaskDto> listTasks(Authentication authentication) {
    return precedentSyncService.listTasks(requireUser(authentication));
  }

  @PostMapping("/tasks")
  public PrecedentSyncTaskDto createTask(@Valid @RequestBody PrecedentSyncTaskRequest request, Authentication authentication) {
    return precedentSyncService.createTask(requireUser(authentication), request);
  }

  @PutMapping("/tasks/{id}")
  public PrecedentSyncTaskDto updateTask(
      @PathVariable long id,
      @Valid @RequestBody PrecedentSyncTaskRequest request,
      Authentication authentication
  ) {
    return precedentSyncService.updateTask(requireUser(authentication), id, request);
  }

  @DeleteMapping("/tasks/{id}")
  public void deleteTask(@PathVariable long id, Authentication authentication) {
    precedentSyncService.deleteTask(requireUser(authentication), id);
  }

  @PostMapping("/tasks/{id}/run")
  public PrecedentSyncRunDto triggerTask(@PathVariable long id, Authentication authentication) {
    return precedentSyncService.triggerTask(requireUser(authentication), id);
  }

  @GetMapping("/runs")
  public List<PrecedentSyncRunDto> listRuns(
      @RequestParam(required = false) Long taskId,
      @RequestParam(defaultValue = "20") int limit,
      Authentication authentication
  ) {
    return precedentSyncService.listRuns(requireUser(authentication), taskId, limit);
  }

  @GetMapping("/runs/{runId}")
  public PrecedentSyncRunDto getRun(@PathVariable long runId, Authentication authentication) {
    return precedentSyncService.getRun(requireUser(authentication), runId);
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("error.session-required");
  }
}
