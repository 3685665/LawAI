const HTML_CONTENT_PATTERN = /<(p|div|br|table|span|strong|h[1-6]|ul|ol|li)\b/i;
const SECTION_HEADING_PATTERN = /^(SONU[CÇ]|HÜKÜM|HUKUM|KARAR|GEREKÇE|GEREKCE|KONU|DAVA|İÇTİHAT|ICTIHAT)\b/i;

export function isPrecedentHtmlContent(value?: string | null) {
  if (!value?.trim()) {
    return false;
  }
  return HTML_CONTENT_PATTERN.test(value);
}

export function stripPrecedentHtml(value: string) {
  return value
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n\n")
    .replace(/<\/div>/gi, "\n")
    .replace(/<\/li>/gi, "\n")
    .replace(/<\/tr>/gi, "\n")
    .replace(/<\/h[1-6]>/gi, "\n\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, "\"")
    .replace(/&#39;/gi, "'")
    .replace(/[ \t\f\r]+/g, " ")
    .replace(/ *\n */g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function getPrecedentPlainContent(content?: string | null, fallback?: string | null) {
  const primary = content?.trim() ?? "";
  if (!primary) {
    return fallback?.trim() ?? "";
  }
  if (isPrecedentHtmlContent(primary)) {
    return stripPrecedentHtml(primary);
  }
  return primary;
}

export function splitPrecedentPlainSections(text: string) {
  return text
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter(Boolean);
}

export function isPrecedentSectionHeading(block: string) {
  const firstLine = block.split("\n")[0]?.trim() ?? block;
  return SECTION_HEADING_PATTERN.test(firstLine) && firstLine.length <= 120;
}
