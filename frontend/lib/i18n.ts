import en from "@/messages/en.json";
import tr from "@/messages/tr.json";

export type Locale = "tr" | "en";

export const defaultLocale: Locale = "tr";

export const messages = {
  tr,
  en
} as const;

export type Messages = typeof tr;

export function isLocale(value: string | null): value is Locale {
  return value === "tr" || value === "en";
}

export function getMessages(locale: Locale): Messages {
  return messages[locale];
}

export function formatMessage(template: string, values: Record<string, string | number>) {
  return Object.entries(values).reduce(
    (text, [key, value]) => text.replaceAll(`{${key}}`, String(value)),
    template
  );
}

export function localeToDateTag(locale: Locale) {
  return locale === "en" ? "en-US" : "tr-TR";
}
