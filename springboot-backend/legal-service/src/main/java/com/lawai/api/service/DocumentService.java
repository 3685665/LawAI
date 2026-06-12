package com.lawai.api.service;

import com.lawai.api.dto.DocumentAnalysisResponse;
import com.lawai.api.dto.DocumentIngestResponse;
import com.lawai.api.dto.KnowledgeDocumentRequest;
import com.lawai.api.dto.KnowledgeIngestRequest;
import com.lawai.api.dto.KnowledgeIngestResponse;
import com.lawai.common.i18n.I18nMessages;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DocumentService {

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");
  private static final int MIN_EXTRACTED_CHARACTERS = 40;
  private static final int DEFAULT_CHUNK_SIZE = 1200;
  private static final int DEFAULT_CHUNK_OVERLAP = 150;

  private final int maxUploadMb;
  private final AiServiceClient aiServiceClient;
  private final I18nMessages i18n;

  public DocumentService(
      @Value("${app.max-upload-mb:25}") int maxUploadMb,
      AiServiceClient aiServiceClient,
      I18nMessages i18n
  ) {
    this.maxUploadMb = maxUploadMb;
    this.aiServiceClient = aiServiceClient;
    this.i18n = i18n;
  }

  public DocumentAnalysisResponse analyze(MultipartFile file) {
    String filename = safeFilename(file);
    String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    byte[] content = readBytes(file);
    List<String> issues = validate(filename, content);
    String summary = i18n.get("document.review-warning");

    if (issues.isEmpty()) {
      try {
        String text = extractText(filename, content);
        summary = text.isEmpty()
            ? i18n.get("document.no-readable-text")
            : i18n.get("document.accepted", text.length());
      } catch (Exception exc) {
        issues.add(i18n.get("document.extraction-failed", exc.getMessage()));
        summary = i18n.get("document.unreadable");
      }
    }

    return new DocumentAnalysisResponse(filename, content.length, contentType, issues, summary);
  }

  public DocumentIngestResponse ingest(MultipartFile file, String topic, String court) {
    String filename = safeFilename(file);
    String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    byte[] content = readBytes(file);
    List<String> warnings = validate(filename, content);
    if (!warnings.isEmpty()) {
      throw new IllegalArgumentException(String.join(" ", warnings));
    }

    String extractedText = extractText(filename, content);
    if (extractedText.length() < MIN_EXTRACTED_CHARACTERS) {
      throw new IllegalArgumentException(i18n.get("error.document-insufficient-text"));
    }

    List<String> chunks = splitIntoChunks(extractedText, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    String documentTopic = StringUtils.hasText(topic) ? topic.trim() : baseNameOf(filename);
    List<KnowledgeDocumentRequest> documents = new ArrayList<>();
    for (int index = 0; index < chunks.size(); index++) {
      String chunk = chunks.get(index);
      documents.add(new KnowledgeDocumentRequest(
          "upload",
          StringUtils.hasText(court) ? court : null,
          null,
          null,
          null,
          null,
          i18n.get("document.chunk-topic", documentTopic, index + 1, chunks.size()),
          preview(chunk, 700),
          chunk
      ));
    }

    KnowledgeIngestResponse ingestResponse = aiServiceClient.ingestKnowledge(new KnowledgeIngestRequest(documents));
    if (ingestResponse.indexed() == 0) {
      throw new IllegalArgumentException(ingestResponse.message());
    }

    return new DocumentIngestResponse(
        filename,
        content.length,
        contentType,
        extractedText.length(),
        chunks.size(),
        ingestResponse.indexed(),
        ingestResponse.storage(),
        ingestResponse.message(),
        preview(extractedText, 500),
        List.of()
    );
  }

  private List<String> validate(String filename, byte[] content) {
    List<String> issues = new ArrayList<>();
    String extension = extensionOf(filename);
    long maxBytes = (long) maxUploadMb * 1024L * 1024L;
    if (content.length == 0) {
      issues.add(i18n.get("error.document-empty"));
    }
    if (content.length > maxBytes) {
      issues.add(i18n.get("error.document-too-large", maxUploadMb));
    }
    if (!SUPPORTED_EXTENSIONS.contains(extension)) {
      issues.add(i18n.get("error.document-unsupported"));
    }
    return issues;
  }

  private String extractText(String filename, byte[] content) {
    String extension = extensionOf(filename);
    try {
      if ("pdf".equals(extension)) {
        try (PDDocument document = Loader.loadPDF(content)) {
          return new PDFTextStripper().getText(document).trim();
        }
      }
      if ("txt".equals(extension)) {
        return new String(content, StandardCharsets.UTF_8).trim();
      }
      if ("docx".equals(extension)) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
          return document.getParagraphs().stream()
              .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText())
              .reduce("", (left, right) -> left + "\n" + right)
              .trim();
        }
      }
      if ("doc".equals(extension)) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(content));
             WordExtractor extractor = new WordExtractor(document)) {
          return extractor.getText().trim();
        }
      }
    } catch (IOException exc) {
      throw new IllegalStateException(i18n.get("error.document-read-failed", exc.getMessage()), exc);
    }
    return "";
  }

  private List<String> splitIntoChunks(String text, int chunkSize, int chunkOverlap) {
    List<String> chunks = new ArrayList<>();
    int index = 0;
    while (index < text.length()) {
      int end = Math.min(text.length(), index + chunkSize);
      chunks.add(text.substring(index, end));
      if (end >= text.length()) {
        break;
      }
      index = Math.max(end - chunkOverlap, index + 1);
    }
    return chunks;
  }

  private String preview(String content, int length) {
    return content.length() <= length ? content : content.substring(0, length) + "...";
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException exc) {
      throw new IllegalStateException(i18n.get("error.file-read-failed", exc.getMessage()), exc);
    }
  }

  private String safeFilename(MultipartFile file) {
    return StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : i18n.get("document.uploaded-default");
  }

  private String extensionOf(String filename) {
    int index = filename.lastIndexOf('.');
    return index < 0 ? "" : filename.substring(index + 1).toLowerCase();
  }

  private String baseNameOf(String filename) {
    int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
    String name = separator < 0 ? filename : filename.substring(separator + 1);
    int dot = name.lastIndexOf('.');
    return dot < 0 ? name : name.substring(0, dot);
  }
}
