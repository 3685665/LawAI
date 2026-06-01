import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "LawAI",
  description: "Next.js, Python ve LangChain ile Turk hukuku odakli AI paneli"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="tr">
      <body>{children}</body>
    </html>
  );
}
