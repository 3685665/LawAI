package com.lawai.api.service;

import org.springframework.web.util.HtmlUtils;

final class PrecedentHtmlSupport {

  private PrecedentHtmlSupport() {
  }

  static String sanitizeHtml(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    String cleaned = html
        .replaceAll("(?is)<script.*?</script>", "")
        .replaceAll("(?is)<style.*?</style>", "")
        .replaceAll("(?is)<iframe.*?</iframe>", "")
        .replaceAll("(?is)<object.*?</object>", "")
        .replaceAll("(?is)<embed.*?>", "")
        .replaceAll("(?i)\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "")
        .replaceAll("(?i)\\s(href|src)\\s*=\\s*(\"|\\')\\s*javascript:[^\"']*(\"|\\')", "");
    return HtmlUtils.htmlUnescape(cleaned).trim();
  }

  static String toPlainText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    String text = html
        .replaceAll("(?is)<script.*?</script>", " ")
        .replaceAll("(?is)<style.*?</style>", " ")
        .replaceAll("(?i)<br\\s*/?>", "\n")
        .replaceAll("(?i)</p>", "\n\n")
        .replaceAll("(?i)</div>", "\n")
        .replaceAll("(?i)</li>", "\n")
        .replaceAll("(?i)</tr>", "\n")
        .replaceAll("(?i)</h[1-6]>", "\n\n")
        .replaceAll("(?i)</td>", "\t")
        .replaceAll("(?s)<[^>]+>", " ");
    text = HtmlUtils.htmlUnescape(text);
    text = text.replace('\u00a0', ' ');
    text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
    text = text.replaceAll(" *\\n *", "\n");
    text = text.replaceAll("\\n{3,}", "\n\n");
    return text.trim();
  }

  static boolean looksLikeHtml(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    return value.matches("(?is).*<(p|div|br|table|span|strong|h[1-6])\\b.*");
  }
}
