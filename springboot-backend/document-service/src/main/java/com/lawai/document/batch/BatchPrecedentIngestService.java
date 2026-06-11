package com.lawai.document.batch;

import com.lawai.document.batch.dto.BatchDocumentJobDto;
import com.lawai.document.client.LegalPrecedentClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class BatchPrecedentIngestService {

  private static final int PAGE_SIZE = 50;

  private final LegalPrecedentClient legalPrecedentClient;

  public BatchPrecedentIngestService(LegalPrecedentClient legalPrecedentClient) {
    this.legalPrecedentClient = legalPrecedentClient;
  }

  public List<PrecedentWorkItem> collectWorkItems(BatchDocumentJobDto job) {
    List<PrecedentCourt> courts = job.precedentCourts().stream()
        .map(value -> PrecedentCourt.valueOf(value.trim().toUpperCase()))
        .toList();
    int maxDocuments = job.precedentMaxDocuments() == null ? 500 : Math.min(Math.max(job.precedentMaxDocuments(), 1), 5000);
    return collectWorkItems(courts, job.precedentDateFrom(), job.precedentDateTo(), maxDocuments);
  }

  public List<PrecedentWorkItem> collectWorkItems(
      List<PrecedentCourt> courts,
      LocalDate dateFrom,
      LocalDate dateTo,
      int maxDocuments
  ) {
    int limit = Math.min(Math.max(maxDocuments, 1), 5000);
    List<PrecedentWorkItem> items = new ArrayList<>();

    for (PrecedentCourt court : courts) {
      int page = 1;
      while (items.size() < limit) {
        LegalPrecedentClient.BatchPageResponse response = legalPrecedentClient.fetchPage(
            court,
            "",
            dateFrom,
            dateTo,
            page,
            PAGE_SIZE
        );
        if (response == null || response.items() == null || response.items().isEmpty()) {
          break;
        }
        for (LegalPrecedentClient.BatchPageItem item : response.items()) {
          items.add(new PrecedentWorkItem(
              court,
              item.sourceId(),
              item.title(),
              item.date(),
              buildStoredPath(court, item.sourceId())
          ));
          if (items.size() >= limit) {
            return items;
          }
        }
        if (!response.hasMore()) {
          break;
        }
        page += 1;
      }
    }
    return items;
  }

  public String fetchPlainText(PrecedentCourt court, String sourceId) {
    LegalPrecedentClient.BatchContentResponse response = legalPrecedentClient.fetchContent(court, sourceId);
    return response == null || response.plainText() == null ? "" : response.plainText().trim();
  }

  private String buildStoredPath(PrecedentCourt court, String sourceId) {
    return "precedent://" + court.name().toLowerCase() + "/" + sourceId;
  }

  public record PrecedentWorkItem(
      PrecedentCourt court,
      String sourceId,
      String title,
      String date,
      String storedPath
  ) {
    public String filename() {
      String safeTitle = title == null ? sourceId : title.replaceAll("[^A-Za-z0-9._-]+", "_");
      if (safeTitle.length() > 80) {
        safeTitle = safeTitle.substring(0, 80);
      }
      return court.name().toLowerCase() + "-" + safeTitle + ".txt";
    }
  }
}
