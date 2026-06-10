package com.lawai.config;

import com.lawai.auth.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MailConfigDiagnostic {

  private static final Logger log = LoggerFactory.getLogger(MailConfigDiagnostic.class);

  private final Environment environment;
  private final EmailService emailService;

  public MailConfigDiagnostic(Environment environment, EmailService emailService) {
    this.environment = environment;
    this.emailService = emailService;
  }

  @EventListener(ApplicationReadyEvent.class)
  void logResolvedMailConfig() {
    String host = environment.getProperty("spring.mail.host", "");
    String username = environment.getProperty("spring.mail.username", "");
    String from = environment.getProperty("app.mail.from", "");
    boolean hasPassword = StringUtils.hasText(environment.getProperty("spring.mail.password", ""));

    String configSource = environment.getProperty("lawai.smtp.config-source", "(bulunamadi)");

    log.info(
        "Mail config: host='{}', username='{}', from='{}', passwordSet={}, senderEnabled={}, configSource='{}', user.dir='{}'",
        host,
        username,
        from,
        hasPassword,
        emailService.isEnabled(),
        configSource,
        System.getProperty("user.dir")
    );

    if (!emailService.isEnabled()) {
      log.warn(
          "SMTP aktif degil. springboot-backend/.env.smtp dosyasini olusturun "
              + "(.env.smtp.example dosyasini .env.smtp olarak kopyalayip sifreyi girin). "
              + "backend/.env ve frontend/.env.local Spring Boot tarafindan okunmaz."
      );
      return;
    }

    String password = environment.getProperty("spring.mail.password", "");
    if (!StringUtils.hasText(password) || "your_smtp_password_here".equals(password)) {
      log.warn(
          "SMTP_PASSWORD hala ornek degerinde. Brevo panelinden gercek SMTP anahtarini "
              + "springboot-backend/.env.smtp dosyasina yazin; aksi halde 'Authentication failed' alinir."
      );
    }
    if ("no-reply@example.com".equals(from)) {
      log.warn(
          "SMTP_FROM ornek adreste. Brevo'da dogrulanmis gonderici e-posta adresinizi .env.smtp icinde SMTP_FROM olarak ayarlayin."
      );
    }
  }
}
