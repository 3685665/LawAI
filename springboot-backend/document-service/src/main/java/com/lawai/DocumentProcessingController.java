package com.lawai.document;

import com.lawai.document.dto.DocumentSearchRequest;
import com.lawai.document.dto.DocumentSearchResponse;
import com.lawai.document.dto.DocumentUploadResponse;
import com.lawai.document.service.DocumentProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DocumentProcessingController {

  private final DocumentProcessingService documentProcessingService;

  public DocumentProcessingController(DocumentProcessingService documentProcessingService) {
    this.documentProcessingService = documentProcessingService;
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentUploadResponse upload(@RequestPart("file") MultipartFile file) {
    return documentProcessingService.upload(file);
  }

  @PostMapping("/search")
  public DocumentSearchResponse search(@Valid @RequestBody DocumentSearchRequest request) {
    return documentProcessingService.search(request.query(), request.limit());
  }
}
