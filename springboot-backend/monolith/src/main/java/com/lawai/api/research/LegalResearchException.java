package com.lawai.api.research;

public class LegalResearchException extends RuntimeException {

  public LegalResearchException(String message) {
    super(message);
  }

  public LegalResearchException(String message, Throwable cause) {
    super(message, cause);
  }
}
