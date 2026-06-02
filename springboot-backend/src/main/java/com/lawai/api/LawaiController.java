package com.lawai.api;

import com.lawai.api.dto.ChatRequest;
import com.lawai.api.dto.ChatResponse;
import com.lawai.api.dto.DocumentAnalysisResponse;
import com.lawai.api.dto.DocumentIngestResponse;
import com.lawai.api.dto.KnowledgeIngestRequest;
import com.lawai.api.dto.KnowledgeIngestResponse;
import com.lawai.api.dto.PetitionRequest;
import com.lawai.api.dto.PetitionResponse;
import com.lawai.api.dto.PrecedentSearchRequest;
import com.lawai.api.dto.PrecedentSearchResponse;
import com.lawai.api.service.AiServiceClient;
import com.lawai.api.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class LawaiController {

  private final AiServiceClient aiServiceClient;
  private final DocumentService documentService;

  public LawaiController(AiServiceClient aiServiceClient, DocumentService documentService) {
    this.aiServiceClient = aiServiceClient;
    this.documentService = documentService;
  }

  @GetMapping("/health")
  public Object health() {
    return aiServiceClient.health();
  }

  @PostMapping("/chat")
  public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    return aiServiceClient.chat(request);
  }

  @PostMapping("/precedents/search")
  public PrecedentSearchResponse searchPrecedents(@Valid @RequestBody PrecedentSearchRequest request) {
    return aiServiceClient.searchPrecedents(request);
  }

  @PostMapping("/petitions")
  public PetitionResponse petitions(@Valid @RequestBody PetitionRequest request) {
    return aiServiceClient.generatePetition(request);
  }

  @PostMapping(value = "/documents/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentAnalysisResponse analyzeDocument(@RequestPart("file") MultipartFile file) {
    return documentService.analyze(file);
  }

  @PostMapping(value = "/documents/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentIngestResponse ingestDocument(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "topic", required = false) String topic,
      @RequestParam(value = "court", required = false) String court
  ) {
    return documentService.ingest(file, topic, court);
  }

  @PostMapping("/knowledge/documents")
  public KnowledgeIngestResponse ingestKnowledge(@Valid @RequestBody KnowledgeIngestRequest request) {
    return aiServiceClient.ingestKnowledge(request);
  }

  @PostMapping("/knowledge/seed-precedents")
  public KnowledgeIngestResponse seedPrecedents() {
    return aiServiceClient.seedPrecedents();
  }
}
