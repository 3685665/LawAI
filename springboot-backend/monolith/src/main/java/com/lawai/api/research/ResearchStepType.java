package com.lawai.api.research;

public enum ResearchStepType {
  PLAN_CREATED("Araştırma planı oluşturuldu"),
  LEGISLATION_IN_PROGRESS("Mevzuat araştırılıyor"),
  LEGISLATION_COMPLETED("Mevzuat araştırması tamamlandı"),
  CASE_LAW_IN_PROGRESS("İçtihat araştırılıyor"),
  CASE_LAW_COMPLETED("İçtihat araştırması tamamlandı"),
  WEB_IN_PROGRESS("Web araştırması yapılıyor"),
  WEB_COMPLETED("Web araştırması tamamlandı"),
  FINAL_ANSWER("Nihai cevap hazırlanıyor");

  private final String label;

  ResearchStepType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
