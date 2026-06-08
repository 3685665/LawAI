package com.lawai.auth.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private final boolean enabled;
  private final String fromAddress;

  public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                      @Value("${spring.mail.host:}") String mailHost,
                      @Value("${spring.mail.username:}") String mailUsername,
                      @Value("${spring.mail.password:}") String mailPassword,
                      @Value("${app.mail.from:no-reply@lawai.local}") String fromAddress) {
    this.mailSender = mailSenderProvider.getIfAvailable();
    this.enabled = this.mailSender != null
        && StringUtils.hasText(mailHost)
        && StringUtils.hasText(mailUsername)
        && StringUtils.hasText(mailPassword);
    this.fromAddress = fromAddress;
  }

  @PostConstruct
  void logMailStatus() {
    if (enabled) {
      log.info("SMTP aktif. Dogrulama e-postalari gonderilecek. from={}", fromAddress);
      return;
    }
    log.warn(
        "SMTP yapilandirilmadi — dogrulama e-postalari gonderilmeyecek. "
            + "springboot-backend/.env.smtp dosyasini olusturun (.env.smtp.example dosyasini kopyalayin)."
    );
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void sendVerificationEmail(String to, String link) {
    String subject = "LawAI - E-posta Dogrulama";
    String text = "Merhaba,\n\nLutfen e-posta adresinizi dogrulamak icin asagidaki baglantiyi kullanin:\n"
        + link + "\n\nTesekkurler.";
    if (!enabled) {
      log.warn("SMTP kapali. Dogrulama linki ({}): {}", to, link);
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      if (fromAddress != null && !fromAddress.isBlank()) {
        message.setFrom(fromAddress);
      }
      message.setTo(to);
      message.setSubject(subject);
      message.setText(text);
      mailSender.send(message);
      log.info("Dogrulama e-postasi gonderildi: {}", to);
    } catch (Exception ex) {
      log.error("Dogrulama e-postasi gonderilemedi ({}): {}", to, ex.getMessage(), ex);
      throw ex;
    }
  }
}
