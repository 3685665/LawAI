package com.lawai.api;

import com.lawai.api.dto.ChatRequest;
import com.lawai.api.dto.ChatResponse;
import com.lawai.api.dto.ChatSessionDto;
import com.lawai.api.dto.DocumentAnalysisResponse;
import com.lawai.api.dto.DocumentIngestResponse;
import com.lawai.api.dto.KnowledgeIngestRequest;
import com.lawai.api.dto.KnowledgeIngestResponse;
import com.lawai.api.dto.PetitionRequest;
import com.lawai.api.dto.PetitionResponse;
import com.lawai.api.dto.PrecedentDto;
import com.lawai.api.dto.PrecedentSearchRequest;
import com.lawai.api.dto.PrecedentSearchResponse;
import com.lawai.api.service.AiServiceClient;
import com.lawai.api.service.ActivityLogService;
import com.lawai.api.service.ChatHistoryService;
import com.lawai.api.service.DocumentService;
import com.lawai.api.service.YargitayPrecedentService;
import com.lawai.auth.model.AuthenticatedUser;
import com.lawai.document.dto.DocumentSearchResult;
import com.lawai.document.service.DocumentProcessingService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LawaiController {

  private final AiServiceClient aiServiceClient;
  private final ChatHistoryService chatHistoryService;
  private final DocumentService documentService;
  private final DocumentProcessingService documentProcessingService;
  private final ActivityLogService activityLogService;
  private final YargitayPrecedentService yargitayPrecedentService;

  public LawaiController(
      AiServiceClient aiServiceClient,
      ChatHistoryService chatHistoryService,
      DocumentService documentService,
      DocumentProcessingService documentProcessingService,
      ActivityLogService activityLogService,
      YargitayPrecedentService yargitayPrecedentService
  ) {
    this.aiServiceClient = aiServiceClient;
    this.chatHistoryService = chatHistoryService;
    this.documentService = documentService;
    this.documentProcessingService = documentProcessingService;
    this.activityLogService = activityLogService;
    this.yargitayPrecedentService = yargitayPrecedentService;
  }

  @GetMapping("/health")
  public Object health() {
    return aiServiceClient.health();
  }

  @PostMapping("/chat")
  public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
    AuthenticatedUser user = requireUser(authentication);
    ChatResponse response = aiServiceClient.chat(request);
    String displayQuestion = request.displayQuestion() == null || request.displayQuestion().isBlank()
        ? request.question()
        : request.displayQuestion();
    ChatSessionDto session = chatHistoryService.saveExchange(user, request.sessionId(), displayQuestion, response);
    activityLogService.logBackend(user, "chat", "Asistan", "AI sohbet analizi yapildi.", "/api/chat");
    return new ChatResponse(response.answer(), response.citations(), response.nextSteps(), response.disclaimer(), session.id());
  }

  @GetMapping("/chat/sessions")
  public List<ChatSessionDto> listChatSessions(Authentication authentication) {
    return chatHistoryService.listForUser(requireUser(authentication));
  }

  @GetMapping("/chat/sessions/{sessionId}")
  public ChatSessionDto getChatSession(@PathVariable String sessionId, Authentication authentication) {
    return chatHistoryService.getForUser(requireUser(authentication), sessionId);
  }

  @DeleteMapping("/chat/sessions/{sessionId}")
  public List<ChatSessionDto> deleteChatSession(@PathVariable String sessionId, Authentication authentication) {
    return chatHistoryService.deleteForUser(requireUser(authentication), sessionId);
  }

  @PostMapping("/precedents/search")
  public PrecedentSearchResponse searchPrecedents(@Valid @RequestBody PrecedentSearchRequest request, Authentication authentication) {
    List<PrecedentDto> results = List.of();
    try {
      results = documentProcessingService.searchWholeDocuments(request.query(), 1)
          .results()
          .stream()
          .map(this::toPrecedent)
          .toList();
    } catch (RuntimeException ignored) {
      // Keep the endpoint stable if the uploaded-document index is empty or unavailable.
    }
    activityLogService.logBackend(requireUser(authentication), "precedent-search", "Emsal Arama", "Emsal karar aramasi yapildi.", "/api/precedents/search");
    return new PrecedentSearchResponse(request.query(), results);
  }

  @PostMapping("/precedents/yargitay/search")
  public PrecedentSearchResponse searchYargitayPrecedents(@Valid @RequestBody PrecedentSearchRequest request, Authentication authentication) {
    List<PrecedentDto> results = yargitayPrecedentService.search(request);
    activityLogService.logBackend(requireUser(authentication), "case-law-search", "Ictihat Arama", "Yargitay ictihat aramasi yapildi.", "/api/precedents/yargitay/search");
    return new PrecedentSearchResponse(request.query(), results);
  }

  @PostMapping("/petitions")
  public PetitionResponse petitions(@Valid @RequestBody PetitionRequest request, Authentication authentication) {
    PetitionResponse response = aiServiceClient.generatePetition(request);
    activityLogService.logBackend(requireUser(authentication), "petition-create", "Dilekce Taslak", "Dilekce taslagi olusturuldu.", "/api/petitions");
    return response;
  }

  @PostMapping(value = "/documents/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentAnalysisResponse analyzeDocument(@RequestPart("file") MultipartFile file, Authentication authentication) {
    DocumentAnalysisResponse response = documentService.analyze(file);
    activityLogService.logBackend(requireUser(authentication), "document-analyze", "Belge Isleme", "Belge on kontrolu yapildi: " + file.getOriginalFilename(), "/api/documents/analyze");
    return response;
  }

  @PostMapping(value = "/documents/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentIngestResponse ingestDocument(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "topic", required = false) String topic,
      @RequestParam(value = "court", required = false) String court,
      Authentication authentication
  ) {
    DocumentIngestResponse response = documentService.ingest(file, topic, court);
    activityLogService.logBackend(requireUser(authentication), "document-ingest", "Belge Isleme", "Belge bilgi bankasina eklendi: " + file.getOriginalFilename(), "/api/documents/ingest");
    return response;
  }

  @PostMapping("/knowledge/documents")
  public KnowledgeIngestResponse ingestKnowledge(@Valid @RequestBody KnowledgeIngestRequest request, Authentication authentication) {
    KnowledgeIngestResponse response = aiServiceClient.ingestKnowledge(request);
    activityLogService.logBackend(requireUser(authentication), "knowledge-ingest", "Bilgi Bankasi", "Bilgi dokumani indekslendi.", "/api/knowledge/documents");
    return response;
  }

  @PostMapping("/knowledge/seed-precedents")
  public KnowledgeIngestResponse seedPrecedents(Authentication authentication) {
    KnowledgeIngestResponse response = aiServiceClient.seedPrecedents();
    activityLogService.logBackend(requireUser(authentication), "knowledge-seed", "Bilgi Bankasi", "Ornek emsal verileri indekslendi.", "/api/knowledge/seed-precedents");
    return response;
  }

  private AuthenticatedUser requireUser(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser user) {
      return user;
    }
    throw new BadCredentialsException("Oturum gerekli.");
  }

  private PrecedentDto toPrecedent(DocumentSearchResult result) {
    return new PrecedentDto(
        "Yuklenen belge",
        "OpenSearch",
        "DOC-" + result.documentId(),
        null,
        null,
        result.filename(),
        preview(result.content(), 450),
        result.content()
    );
  }

  private String preview(String content, int maxLength) {
    if (content == null || content.length() <= maxLength) {
      return content;
    }
    return content.substring(0, maxLength) + "...";
  }
}
