package com.lawai.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailService {

  private final JavaMailSender mailSender;
  private final boolean enabled;
  private final String fromAddress;

  public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                      @Value("${spring.mail.host:}") String mailHost,
                      @Value("${spring.mail.username:}") String mailUsername,
                      @Value("${app.mail.from:no-reply@lawai.local}") String fromAddress) {
    this.mailSender = mailSenderProvider.getIfAvailable();
    // Only enable sending if a mail sender exists and basic SMTP configuration is present
    this.enabled = this.mailSender != null && StringUtils.hasText(mailHost) && StringUtils.hasText(mailUsername);
    this.fromAddress = fromAddress;
    if (this.mailSender != null && !this.enabled) {
      System.out.println("[EmailService] Mail sender autoconfigured but SMTP_HOST or SMTP_USERNAME missing — disabling actual send to avoid runtime authentication errors.");
    }
  }

  public void sendVerificationEmail(String to, String link) {
    String subject = "LawAI - E-posta Dogrulama";
    String text = "Merhaba,\n\nLutfen e-posta adresinizi dogrulamak icin asagidaki baglantiyi kullanin:\n" + link + "\n\nTesekkurler.";
    if (!enabled) {
      System.out.println("[EmailService] Mail sender not configured. Verification link for " + to + ": " + link);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    if (fromAddress != null && !fromAddress.isBlank()) {
      message.setFrom(fromAddress);
    }
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);
    mailSender.send(message);
  }
}
