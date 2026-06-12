package com.lawai.document.batch;

import com.lawai.common.model.AuthenticatedUser;
import com.lawai.document.batch.dto.BatchDocumentJobDto;
import com.lawai.document.batch.dto.BatchDocumentJobRequest;
import com.lawai.document.batch.dto.BatchDocumentRunDto;
import com.lawai.document.batch.dto.DirectoryBrowseResponse;
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
@RequestMapping("/api/batch-documents")
public class BatchDocumentJobController {

  private final BatchDocumentProcessingService processingService;
  private final BatchDirectoryBrowseService directoryBrowseService;

  public BatchDocumentJobController(
      BatchDocumentProcessingService processingService,
      BatchDirectoryBrowseService directoryBrowseService
  ) {
    this.processingService = processingService;
    this.directoryBrowseService = directoryBrowseService;
  }

  @GetMapping("/jobs")
  public List<BatchDocumentJobDto> listJobs(Authentication authentication) {
    return processingService.listJobs(requireUser(authentication));
  }

  @PostMapping("/jobs")
  public BatchDocumentJobDto createJob(@Valid @RequestBody BatchDocumentJobRequest request, Authentication authentication) {
    return processingService.createJob(requireUser(authentication), request);
  }

  @PutMapping("/jobs/{id}")
  public BatchDocumentJobDto updateJob(
      @PathVariable long id,
      @Valid @RequestBody BatchDocumentJobRequest request,
      Authentication authentication
  ) {
    return processingService.updateJob(requireUser(authentication), id, request);
  }

  @DeleteMapping("/jobs/{id}")
  public void deleteJob(@PathVariable long id, Authentication authentication) {
    processingService.deleteJob(requireUser(authentication), id);
  }

  @PostMapping("/jobs/{id}/run")
  public BatchDocumentRunDto triggerJob(@PathVariable long id, Authentication authentication) {
    return processingService.triggerJob(requireUser(authentication), id);
  }

  @GetMapping("/runs")
  public List<BatchDocumentRunDto> listRuns(
      @RequestParam(required = false) Long jobId,
      @RequestParam(defaultValue = "20") int limit,
      Authentication authentication
  ) {
    return processingService.listRuns(requireUser(authentication), jobId, limit);
  }

  @GetMapping("/runs/{runId}")
  public BatchDocumentRunDto getRun(@PathVariable long runId, Authentication authentication) {
    return processingService.getRun(requireUser(authentication), runId);
  }

  @GetMapping("/directories")
  public DirectoryBrowseResponse browseDirectories(
      @RequestParam(required = false) String path,
      Authentication authentication
  ) {
    return directoryBrowseService.browse(requireUser(authentication), path);
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("error.session-required");
  }
}
