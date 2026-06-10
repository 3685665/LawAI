package com.lawai.document.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class PdfTextExtractionClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public PdfTextExtractionClient(
      @Value("${app.ai-base-url:http://localhost:8000/api}") String aiBaseUrl,
      ObjectMapper objectMapper
  ) {
    this.restClient = RestClient.builder().baseUrl(aiBaseUrl).build();
    this.objectMapper = objectMapper;
  }

  public String extract(MultipartFile file, String filename) {
    return extract(readBytes(file), filename);
  }

  public String extract(byte[] content, String filename) {
    ByteArrayResource resource = new ByteArrayResource(content) {
      @Override
      public String getFilename() {
        return filename;
      }
    };

    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(contentTypeFor(filename));
    HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart);

    try {
      PdfTextExtractionResponse response = restClient.post()
          .uri("/documents/extract-text")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(body)
          .retrieve()
          .body(PdfTextExtractionResponse.class);
      if (response == null || response.text() == null) {
        throw new IllegalStateException("Metin cikarma servisi bos yanit dondu.");
      }
      return response.text().trim();
    } catch (Exception exception) {
      throw new IllegalStateException("Metin cikarma servisi hata verdi: " + readableMessage(exception), exception);
    }
  }

  private MediaType contentTypeFor(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    return switch (filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
      case "pdf" -> MediaType.APPLICATION_PDF;
      case "txt" -> MediaType.TEXT_PLAIN;
      case "doc" -> MediaType.parseMediaType("application/msword");
      case "docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException exception) {
      throw new IllegalStateException("Dosya okunamadi: " + exception.getMessage(), exception);
    }
  }

  private String readableMessage(Exception exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    int jsonStart = message.indexOf('{');
    if (jsonStart >= 0) {
      try {
        ErrorResponse error = objectMapper.readValue(message.substring(jsonStart), ErrorResponse.class);
        if (error.detail() != null && !error.detail().isBlank()) {
          return error.detail();
        }
      } catch (Exception ignored) {
        return message;
      }
    }
    return message;
  }

  private record PdfTextExtractionResponse(String filename, int extractedCharacters, String text) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ErrorResponse(String detail) {
  }
}
