package com.lawai.auth.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

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
      log.info("SMTP aktif. Dogrulama ve sifre sifirlama e-postalari gonderilecek. from={}", fromAddress);
      return;
    }
    log.warn(
        "SMTP yapilandirilmadi — dogrulama ve sifre sifirlama e-postalari gonderilmeyecek. "
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
    String html = buildHtmlEmail(
        "Lutfen e-posta adresinizi dogrulamak icin asagidaki baglantiya tiklayin:",
        "E-posta Adresimi Dogrula",
        link,
        null
    );
    if (!enabled) {
      log.warn("SMTP kapali. Dogrulama linki ({}): {}", to, link);
      return;
    }
    try {
      sendHtmlEmail(to, subject, text, html);
      log.info("Dogrulama e-postasi gonderildi: {}", to);
    } catch (Exception ex) {
      log.error("Dogrulama e-postasi gonderilemedi ({}): {}", to, ex.getMessage(), ex);
      throw ex;
    }
  }

  public void sendPasswordResetEmail(String to, String link) {
    String subject = "LawAI - Sifre Sifirlama";
    String text = "Merhaba,\n\nSifrenizi sifirlamak icin asagidaki baglantiyi kullanin:\n"
        + link + "\n\nBaglanti 2 saat gecerlidir. Bu istegi siz yapmadiysaniz bu e-postayi yok sayin.\n\nTesekkurler.";
    String html = buildHtmlEmail(
        "Sifrenizi sifirlamak icin asagidaki baglantiya tiklayin:",
        "Sifremi Sifirla",
        link,
        "Baglanti 2 saat gecerlidir. Bu istegi siz yapmadiysaniz bu e-postayi yok sayin."
    );
    if (!enabled) {
      log.warn("SMTP kapali. Sifre sifirlama linki ({}): {}", to, link);
      return;
    }
    try {
      sendHtmlEmail(to, subject, text, html);
      log.info("Sifre sifirlama e-postasi gonderildi: {}", to);
    } catch (Exception ex) {
      log.error("Sifre sifirlama e-postasi gonderilemedi ({}): {}", to, ex.getMessage(), ex);
      throw ex;
    }
  }

  private String buildHtmlEmail(String intro, String linkLabel, String link, String footerNote) {
    String safeLink = HtmlUtils.htmlEscape(link);
    StringBuilder html = new StringBuilder();
    html.append("<html><body style=\"font-family:sans-serif;line-height:1.5;color:#111;\">");
    html.append("<p>Merhaba,</p>");
    html.append("<p>").append(HtmlUtils.htmlEscape(intro)).append("</p>");
    html.append("<p><a href=\"").append(safeLink).append("\" style=\"color:#2563eb;font-weight:600;\">")
        .append(HtmlUtils.htmlEscape(linkLabel)).append("</a></p>");
    html.append("<p style=\"color:#666;font-size:12px;\">Baglanti calismazsa su adresi tarayiciniza yapistirin:<br>");
    html.append("<a href=\"").append(safeLink).append("\" style=\"color:#2563eb;\">")
        .append(safeLink).append("</a></p>");
    if (footerNote != null && !footerNote.isBlank()) {
      html.append("<p>").append(HtmlUtils.htmlEscape(footerNote)).append("</p>");
    }
    html.append("<p>Tesekkurler.</p>");
    html.append("</body></html>");
    return html.toString();
  }

  private void sendHtmlEmail(String to, String subject, String text, String html) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      if (fromAddress != null && !fromAddress.isBlank()) {
        helper.setFrom(fromAddress);
      }
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text, html);
      mailSender.send(message);
    } catch (MessagingException ex) {
      throw new IllegalStateException("E-posta olusturulamadi", ex);
    }
  }
}
