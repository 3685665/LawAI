package com.lawai.api;

import com.lawai.api.dto.CaseAiActionRequest;
import com.lawai.api.dto.CaseAiActionResponse;
import com.lawai.api.dto.CaseCreateRequest;
import com.lawai.api.dto.CaseDocumentPatchResponse;
import com.lawai.api.dto.CaseDocumentUpdateRequest;
import com.lawai.api.dto.CaseRecordResponse;
import com.lawai.api.dto.CaseTemplatesResponse;
import com.lawai.common.client.ActivityLogClient;
import com.lawai.api.service.CaseAiActionService;
import com.lawai.api.service.CaseService;
import com.lawai.common.model.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

  private final CaseService caseService;
  private final CaseAiActionService caseAiActionService;
  private final ActivityLogClient activityLogClient;

  public CaseController(CaseService caseService, CaseAiActionService caseAiActionService, ActivityLogClient activityLogClient) {
    this.caseService = caseService;
    this.caseAiActionService = caseAiActionService;
    this.activityLogClient = activityLogClient;
  }

  @GetMapping("/templates")
  public CaseTemplatesResponse templates() {
    return caseService.templates();
  }

  @GetMapping
  public List<CaseRecordResponse> listCases() {
    return caseService.listCases();
  }

  @PostMapping("/seed-samples")
  public List<CaseRecordResponse> seedSamples(Authentication authentication) {
    List<CaseRecordResponse> response = caseService.seedSamples();
    activityLogClient.logBackend(requireUser(authentication), "case-seed", "Davalar", "Ornek dava kayitlari yuklendi.", "/api/cases/seed-samples");
    return response;
  }

  @PostMapping
  public CaseRecordResponse createCase(@Valid @RequestBody CaseCreateRequest request, Authentication authentication) {
    CaseRecordResponse response = caseService.createCase(request);
    activityLogClient.logBackend(requireUser(authentication), "case-create", "Davalar", "Dava kaydi olusturuldu: " + response.fileTitle(), "/api/cases");
    return response;
  }

  @GetMapping("/{caseId}")
  public CaseRecordResponse getCase(@PathVariable String caseId) {
    return caseService.getCase(caseId);
  }

  @PutMapping("/{caseId}")
  public CaseRecordResponse updateCase(
      @PathVariable String caseId,
      @Valid @RequestBody CaseCreateRequest request,
      Authentication authentication
  ) {
    CaseRecordResponse response = caseService.updateCase(caseId, request);
    activityLogClient.logBackend(requireUser(authentication), "case-update", "Davalar", "Dava kaydi guncellendi: " + response.fileTitle(), "/api/cases/" + caseId);
    return response;
  }

  @PostMapping("/{caseId}/ai-actions")
  public CaseAiActionResponse runAiAction(
      @PathVariable String caseId,
      @Valid @RequestBody CaseAiActionRequest request,
      Authentication authentication
  ) {
    CaseAiActionResponse response = caseAiActionService.run(caseId, request.action());
    activityLogClient.logBackend(requireUser(authentication), "case-ai-action", "Davalar", response.title() + " AI islemi calistirildi.", "/api/cases/" + caseId + "/ai-actions");
    return response;
  }

  @DeleteMapping("/{caseId}")
  public List<CaseRecordResponse> deleteCase(@PathVariable String caseId, Authentication authentication) {
    List<CaseRecordResponse> response = caseService.deleteCase(caseId);
    activityLogClient.logBackend(requireUser(authentication), "case-delete", "Davalar", "Dava kaydi silindi: " + caseId, "/api/cases/" + caseId);
    return response;
  }

  @PatchMapping("/{caseId}/documents/{documentId}")
  public CaseDocumentPatchResponse updateDocument(
      @PathVariable String caseId,
      @PathVariable String documentId,
      @RequestBody CaseDocumentUpdateRequest request,
      Authentication authentication
  ) {
    CaseDocumentPatchResponse response = caseService.updateDocument(caseId, documentId, request);
    activityLogClient.logBackend(requireUser(authentication), "case-document-update", "Davalar", "Dava belge durumu guncellendi: " + documentId, "/api/cases/" + caseId + "/documents/" + documentId);
    return response;
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("error.session-required");
  }
}
