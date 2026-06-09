import { Document, Packer, Paragraph, TextRun } from "docx";

const PETITION_FONT = "Times New Roman";
const PETITION_FONT_SIZE = 24;

export function sanitizePetitionFileName(value: string): string {
  const trimmed = value.trim() || "dilekce";
  return trimmed.replace(/[<>:"/\\|?*\x00-\x1f]/g, "_").slice(0, 120);
}

export async function buildPetitionDocxBlob(body: string): Promise<Blob> {
  const paragraphs = body.split(/\r?\n/).map(
    (line) =>
      new Paragraph({
        spacing: { after: 80 },
        children: [
          new TextRun({
            text: line.length > 0 ? line : " ",
            font: PETITION_FONT,
            size: PETITION_FONT_SIZE,
          }),
        ],
      })
  );

  const doc = new Document({
    sections: [{ children: paragraphs }],
  });

  return Packer.toBlob(doc);
}
