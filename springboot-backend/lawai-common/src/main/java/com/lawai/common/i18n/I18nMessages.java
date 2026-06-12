package com.lawai.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class I18nMessages {

  private final MessageSource messageSource;

  public I18nMessages(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  public String get(String code, Object... args) {
    return messageSource.getMessage(code, args, code, LocaleContextHolder.getLocale());
  }

  public String detail(String value) {
    if (value == null || value.isBlank()) {
      return get("error.invalid-request");
    }
    return get(value);
  }
}
