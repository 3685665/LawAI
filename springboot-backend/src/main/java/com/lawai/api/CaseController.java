package com.lawai.api;

import com.lawai.api.dto.CaseCreateRequest;
import com.lawai.api.dto.CaseDocumentPatchResponse;
import com.lawai.api.dto.CaseDocumentUpdateRequest;
import com.lawai.api.dto.CaseRecordResponse;
import com.lawai.api.dto.CaseTemplatesResponse;
import com.lawai.api.service.CaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

  private final CaseService caseService;

  public CaseController(CaseService caseService) {
    this.caseService = caseService;
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
  public List<CaseRecordResponse> seedSamples() {
    return caseService.seedSamples();
  }

  @PostMapping
  public CaseRecordResponse createCase(@Valid @RequestBody CaseCreateRequest request) {
    return caseService.createCase(request);
  }

  @GetMapping("/{caseId}")
  public CaseRecordResponse getCase(@PathVariable String caseId) {
    return caseService.getCase(caseId);
  }

  @DeleteMapping("/{caseId}")
  public List<CaseRecordResponse> deleteCase(@PathVariable String caseId) {
    return caseService.deleteCase(caseId);
  }

  @PatchMapping("/{caseId}/documents/{documentId}")
  public CaseDocumentPatchResponse updateDocument(
      @PathVariable String caseId,
      @PathVariable String documentId,
      @RequestBody CaseDocumentUpdateRequest request
  ) {
    return caseService.updateDocument(caseId, documentId, request);
  }
}
