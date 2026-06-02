export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080/api";

export type Precedent = {
  court: string;
  chamber?: string | null;
  docketNo?: string | null;
  decisionNo?: string | null;
  date?: string | null;
  topic: string;
  summary: string;
};

export type CaseDocument = {
  id: string;
  title: string;
  detail: string;
  required: boolean;
  group: string;
  completed: boolean;
};

export type CaseTemplate = {
  caseType: string;
  label: string;
  title: string;
  courtHint: string;
  summary: string;
  documents: CaseDocument[];
};

export type CaseRecord = {
  id: string;
  caseType: string;
  caseLabel: string;
  clientName: string;
  opponentName: string;
  courtName: string;
  subject: string;
  summary: string;
  requiredDocumentCount: number;
  completedRequiredDocumentCount: number;
  progress: number;
  documents: CaseDocument[];
  createdAt: string;
  updatedAt: string;
};

export type CaseTemplatesResponse = { templates: CaseTemplate[] };
export type CaseDocumentPatchResponse = { caseRecord: CaseRecord; cases: CaseRecord[] };

export async function postJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function patchJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function deleteJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "DELETE"
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function seedSamples<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST"
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function uploadMultipart<T>(path: string, form: FormData): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    body: form
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function checkHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE}/health`, { cache: "no-store" });
    return response.ok;
  } catch {
    return false;
  }
}

async function readError(response: Response): Promise<string> {
  try {
    const body = await response.json();
    return body.detail ?? body.message ?? `API istegi basarisiz: ${response.status}`;
  } catch {
    return `API istegi basarisiz: ${response.status}`;
  }
}
