package com.lawai.api.research;

public enum ResearchStepType {
  PLAN_CREATED("research.step.plan-created"),
  LEGISLATION_IN_PROGRESS("research.step.legislation-in-progress"),
  LEGISLATION_COMPLETED("research.step.legislation-completed"),
  CASE_LAW_IN_PROGRESS("research.step.case-law-in-progress"),
  CASE_LAW_COMPLETED("research.step.case-law-completed"),
  WEB_IN_PROGRESS("research.step.web-in-progress"),
  WEB_COMPLETED("research.step.web-completed"),
  FINAL_ANSWER("research.step.final-answer");

  private final String messageCode;

  ResearchStepType(String messageCode) {
    this.messageCode = messageCode;
  }

  public String messageCode() {
    return messageCode;
  }
}
