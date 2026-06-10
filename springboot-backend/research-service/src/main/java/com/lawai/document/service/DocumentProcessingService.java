package com.lawai.document.service;

import com.lawai.document.dto.DocumentSearchResponse;
import com.lawai.document.dto.DocumentSearchResult;
import com.lawai.document.dto.DocumentUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentProcessingService {

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".txt", ".doc", ".docx");
  private static final int MIN_TEXT_LENGTH = 40;

  private final DocumentProcessingProperties properties;
  private final PdfTextExtractionClient pdfTextExtractionClient;
  private final DocumentEmbeddingService embeddingService;
  private final DocumentRepository documentRepository;
  private final OpenSearchDocumentClient openSearchClient;

  public DocumentProcessingService(
      DocumentProcessingProperties properties,
      PdfTextExtractionClient pdfTextExtractionClient,
      DocumentEmbeddingService embeddingService,
      DocumentRepository documentRepository,
      OpenSearchDocumentClient openSearchClient
  ) {
    this.properties = properties;
    this.pdfTextExtractionClient = pdfTextExtractionClient;
    this.embeddingService = embeddingService;
    this.documentRepository = documentRepository;
    this.openSearchClient = openSearchClient;
  }

  public DocumentUploadResponse upload(MultipartFile file) {
    String filename = safeFilename(file);
    if (!isSupportedExtension(filename)) {
      throw new IllegalArgumentException("Belge isleme hatti PDF, Word ve metin dosyalarini destekler.");
    }
    String text = pdfTextExtractionClient.extract(file, filename);
    if (text.length() < MIN_TEXT_LENGTH) {
      throw new IllegalArgumentException("Dosyadan yeterli metin cikarilamadi. Taranmis PDF olabilir; OCR destegi henuz eklenmedi.");
    }

    List<DocumentChunk> chunks = chunk(text).stream()
        .map(chunk -> new DocumentChunk(chunk.index(), chunk.content(), embeddingService.embedLiteral(chunk.content())))
        .toList();

    long documentId = documentRepository.createDocument(
        filename,
        file.getContentType() == null ? "application/pdf" : file.getContentType(),
        file.getSize(),
        "",
        text
    );
    List<StoredChunk> storedChunks = documentRepository.createChunks(documentId, chunks);
    int opensearchIndexed = openSearchClient.indexChunks(filename, storedChunks);

    return new DocumentUploadResponse(
        documentId,
        filename,
        null,
        text.length(),
        chunks.size(),
        storedChunks.size(),
        opensearchIndexed,
        storedChunks.size(),
        "Belge bellek uzerinden islendi; metin PostgreSQL'e yazildi, chunklar PostgreSQL/OpenSearch/pgvector hattina alindi.",
        summarize(text, filename, chunks.size()),
        preview(text, 1200)
    );
  }

  public DocumentSearchResponse search(String query, Integer requestedLimit) {
    int limit = Math.min(Math.max(requestedLimit == null ? 5 : requestedLimit, 1), 25);
    List<DocumentSearchResult> openSearchResults = openSearchClient.search(query, limit);
    if (!openSearchResults.isEmpty()) {
      return new DocumentSearchResponse(query, "opensearch", openSearchResults);
    }
    String queryEmbedding = embeddingService.embedLiteral(query);
    return new DocumentSearchResponse(query, "pgvector", documentRepository.searchByVector(queryEmbedding, limit));
  }

  public DocumentSearchResponse searchWholeDocuments(String query, Integer requestedLimit) {
    int limit = Math.min(Math.max(requestedLimit == null ? 1 : requestedLimit, 1), 5);
    return new DocumentSearchResponse(query, "opensearch", openSearchClient.searchWholeDocuments(query, limit));
  }

  private List<RawChunk> chunk(String text) {
    int chunkSize = Math.max(properties.chunkSize(), 200);
    int overlap = Math.min(Math.max(properties.chunkOverlap(), 0), chunkSize - 1);
    List<RawChunk> chunks = new ArrayList<>();
    int start = 0;
    int index = 0;
    while (start < text.length()) {
      int end = Math.min(text.length(), start + chunkSize);
      chunks.add(new RawChunk(index++, text.substring(start, end)));
      if (end >= text.length()) {
        break;
      }
      start = Math.max(end - overlap, start + 1);
    }
    return chunks;
  }

  private boolean isSupportedExtension(String filename) {
    String lower = filename.toLowerCase(Locale.ROOT);
    return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
  }

  private String safeFilename(MultipartFile file) {
    String original = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "document.pdf";
    String name = Path.of(original).getFileName().toString();
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private String summarize(String text, String filename, int chunkCount) {
    String normalized = normalizeWhitespace(text);
    String firstSentence = firstSentence(normalized);
    return "Belge icerigi ozeti: " + firstSentence
        + " Dosya adi: " + filename
        + ". Toplam " + text.length() + " karakter metin cikarildi ve " + chunkCount + " chunk olusturuldu.";
  }

  private String firstSentence(String text) {
    if (!StringUtils.hasText(text)) {
      return "Belgeden okunabilir metin cikarildi.";
    }
    int max = Math.min(text.length(), 700);
    int sentenceEnd = -1;
    for (String marker : List.of(". ", "? ", "! ", "\n")) {
      int index = text.indexOf(marker);
      if (index > 80 && index < max) {
        sentenceEnd = sentenceEnd < 0 ? index + 1 : Math.min(sentenceEnd, index + 1);
      }
    }
    String summary = sentenceEnd > 0 ? text.substring(0, sentenceEnd) : text.substring(0, max);
    return summary.trim() + (summary.length() < text.length() && !summary.endsWith(".") ? "..." : "");
  }

  private String preview(String text, int maxLength) {
    String normalized = normalizeWhitespace(text);
    return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim() + "...";
  }

  private String normalizeWhitespace(String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").trim();
  }

  private record RawChunk(int index, String content) {
  }
}
