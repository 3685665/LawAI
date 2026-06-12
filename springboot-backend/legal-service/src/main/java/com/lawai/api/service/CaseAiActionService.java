package com.lawai.api.service;

import com.lawai.api.dto.CaseAiActionResponse;
import com.lawai.api.dto.CaseDocumentDto;
import com.lawai.api.dto.CaseExpenseDto;
import com.lawai.api.dto.CaseNoteDto;
import com.lawai.api.dto.CasePartyDto;
import com.lawai.api.dto.CaseRecordResponse;
import com.lawai.api.dto.ChatRequest;
import com.lawai.api.dto.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class CaseAiActionService {

  private final CaseService caseService;
  private final AiServiceClient aiServiceClient;

  public CaseAiActionService(CaseService caseService, AiServiceClient aiServiceClient) {
    this.caseService = caseService;
    this.aiServiceClient = aiServiceClient;
  }

  public CaseAiActionResponse run(String caseId, String action) {
    CaseRecordResponse legalCase = caseService.getCase(caseId);
    CaseAiActionDefinition definition = definition(action);
    ChatResponse response = aiServiceClient.chat(new ChatRequest(
        buildPrompt(legalCase, definition),
        "analysis",
        true,
        null,
        definition.title() + " - " + legalCase.fileTitle()
    ));
    return new CaseAiActionResponse(
        definition.key(),
        definition.title(),
        response.answer(),
        response.nextSteps() == null ? List.of() : response.nextSteps(),
        response.disclaimer()
    );
  }

  private CaseAiActionDefinition definition(String action) {
    String normalized = normalize(action);
    return switch (normalized) {
      case "strategy" -> new CaseAiActionDefinition(
          "strategy",
          "Dava Stratejisi",
          "Müvekkil perspektifinden dava stratejisi üret. Güçlü iddiaları, ispat planını, usuli hamleleri ve kısa vadeli aksiyonları ayır."
      );
      case "hearing_statement" -> new CaseAiActionDefinition(
          "hearing_statement",
          "Duruşma Beyanı",
          "Bir sonraki duruşmada kullanılabilecek kısa, düzenli ve ihtiyatlı beyan taslağı hazırla."
      );
      case "risk" -> new CaseAiActionDefinition(
          "risk",
          "Risk Analizi",
          "Dosyadaki zayıf noktaları, ispat boşluklarını, usul risklerini ve karşı tarafın muhtemel itirazlarını analiz et."
      );
      case "evidence" -> new CaseAiActionDefinition(
          "evidence",
          "Delil Listesi",
          "Mevcut dosya bilgilerine göre delil listesini ve her delilin hangi vakıayı desteklediğini tablo mantığında açıkla."
      );
      case "missing_documents" -> new CaseAiActionDefinition(
          "missing_documents",
          "Eksik Belge Kontrolü",
          "Dava türü ve checklist'e göre eksik belge, bilgi ve tamamlanması gereken hazırlıkları belirle."
      );
      case "client_summary" -> new CaseAiActionDefinition(
          "client_summary",
          "Müvekkil Özeti",
          "Müvekkile gönderilebilecek sade, samimi ve anlaşılır durum açıklaması yaz."
      );
      case "precedent_match" -> new CaseAiActionDefinition(
          "precedent_match",
          "Emsal Eşleştirme",
          "Dava konusu için aranması gereken Yargıtay/Danıştay/AYM emsal temalarını ve arama sorgularını öner."
      );
      case "petition_suggestions" -> new CaseAiActionDefinition(
          "petition_suggestions",
          "Dilekçe Önerisi",
          "Dosyaya göre yazılabilecek dilekçeleri, amaçlarını ve öncelik sırasını öner."
      );
      case "hearing_notes" -> new CaseAiActionDefinition(
          "hearing_notes",
          "Duruşma Notu",
          "Duruşmada dikkat edilecek noktaları, sorulacak soruları ve hatırlatıcıları hazırla."
      );
      case "case_score" -> new CaseAiActionDefinition(
          "case_score",
          "Dava Puanlama",
          "Kazanma olasılığını kesin sonuç gibi sunmadan; güçlü/zayıf yönler ve belirsizlikleri puanlı değerlendir."
      );
      case "opponent_analysis" -> new CaseAiActionDefinition(
          "opponent_analysis",
          "Karşı Taraf Analizi",
          "Karşı tarafın muhtemel stratejisini, itirazlarını ve bunlara hazırlık önerilerini çıkar."
      );
      case "appeal_review" -> new CaseAiActionDefinition(
          "appeal_review",
          "Kanun Yolu Değerlendirmesi",
          "İstinaf/temyiz değerlendirmesi için kontrol edilmesi gereken karar, süre, miktar ve gerekçe başlıklarını analiz et."
      );
      case "calendar" -> new CaseAiActionDefinition(
          "calendar",
          "Dava Takvimi",
          "Kronolojik olay çizelgesi, yaklaşan süreler ve yapılacaklar listesi hazırla."
      );
      default -> throw new IllegalArgumentException("Desteklenmeyen dava AI islemi: " + action);
    };
  }

  private String buildPrompt(CaseRecordResponse legalCase, CaseAiActionDefinition definition) {
    return """
        Türk hukuku bağlamında çalışan bir avukat asistanısın.
        Dosyada olmayan olay, tarih, belge, karar veya taraf bilgisi uydurma.
        Belirsiz bilgi varsa açıkça "dosyada görünmüyor" de.
        Yanıtı uygulanabilir, kısa başlıklı ve avukatın doğrudan kullanabileceği biçimde ver.

        İstenen işlem: %s
        İşlem yönergesi: %s

        Dava dosyası:
        %s
        """.formatted(definition.title(), definition.instruction(), formatCase(legalCase));
  }

  private String formatCase(CaseRecordResponse legalCase) {
    return String.join("\n",
        "Başlık: " + fallback(legalCase.fileTitle()),
        "Dava türü: " + fallback(legalCase.caseLabel()),
        "Dosya no: " + fallback(legalCase.caseNumber()),
        "Mahkeme: " + fallback(legalCase.courtName()),
        "Şehir: " + fallback(legalCase.city()),
        "Notlar: " + fallback(legalCase.notes()),
        "Tamamlanma: %" + legalCase.progress(),
        "Taraflar:\n" + formatParties(legalCase.parties()),
        "Masraflar:\n" + formatExpenses(legalCase.expenses()),
        "Dosya notları:\n" + formatNotes(legalCase.caseNotes()),
        "Belge checklist:\n" + formatDocuments(legalCase.documents())
    );
  }

  private String formatParties(List<CasePartyDto> parties) {
    if (parties == null || parties.isEmpty()) {
      return "-";
    }
    return parties.stream()
        .map(party -> "- " + fallback(party.role()) + ": " + fallback(party.name()) + " | " + fallback(party.contact()))
        .toList()
        .stream()
        .reduce((left, right) -> left + "\n" + right)
        .orElse("-");
  }

  private String formatExpenses(List<CaseExpenseDto> expenses) {
    if (expenses == null || expenses.isEmpty()) {
      return "-";
    }
    return expenses.stream()
        .map(expense -> "- " + fallback(expense.title()) + " | " + expense.amount() + " TL | " + fallback(expense.description()))
        .toList()
        .stream()
        .reduce((left, right) -> left + "\n" + right)
        .orElse("-");
  }

  private String formatNotes(List<CaseNoteDto> notes) {
    if (notes == null || notes.isEmpty()) {
      return "-";
    }
    return notes.stream()
        .map(note -> "- " + fallback(note.title()) + ": " + fallback(note.text()))
        .toList()
        .stream()
        .reduce((left, right) -> left + "\n" + right)
        .orElse("-");
  }

  private String formatDocuments(List<CaseDocumentDto> documents) {
    if (documents == null || documents.isEmpty()) {
      return "-";
    }
    return documents.stream()
        .map(document -> "- [%s] %s (%s): %s".formatted(
            document.completed() ? "tamam" : "eksik",
            fallback(document.title()),
            document.required() ? "zorunlu" : "opsiyonel",
            fallback(document.detail())
        ))
        .toList()
        .stream()
        .reduce((left, right) -> left + "\n" + right)
        .orElse("-");
  }

  private String fallback(String value) {
    return StringUtils.hasText(value) ? value.trim() : "-";
  }

  private String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
  }

  private record CaseAiActionDefinition(String key, String title, String instruction) {
  }
}
