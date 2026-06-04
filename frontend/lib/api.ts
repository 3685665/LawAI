export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080/api";

export type Precedent = {
  court: string;
  chamber?: string | null;
  docketNo?: string | null;
  decisionNo?: string | null;
  date?: string | null;
  topic: string;
  summary: string;
  content?: string | null;
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
export type AuthUser = {
  id: string;
  name: string;
  email: string;
  role?: string | null;
  createdAt?: string | null;
  lastLoginAt?: string | null;
};

export type AuthSessionResponse = { user: AuthUser };
export type AuthPasswordResetResponse = { message: string; resetTokenPreview?: string | null; expiresAt?: string | null; resetLinkPreview?: string | null };
export type FeedbackRecord = {
  id: string;
  userName?: string | null;
  userEmail?: string | null;
  type: string;
  subject: string;
  message: string;
  status: string;
  createdAt: string;
};
export type FeedbackSubmissionResponse = { message: string; feedback: FeedbackRecord };
export type FeedbackStatus = "received" | "read" | "resolved";
export type FeedbackType = "hata" | "ozellik" | "genel";
export type FeedbackUpdatePayload = {
  type: FeedbackType;
  subject: string;
  message: string;
  status: FeedbackStatus;
};

export async function postJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, { cache: "no-store", credentials: "include" });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function patchJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "PATCH",
    credentials: "include",
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
    method: "DELETE",
    credentials: "include"
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function seedSamples<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    credentials: "include"
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function uploadMultipart<T>(path: string, form: FormData): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    credentials: "include",
    body: form
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function checkHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE}/health`, { cache: "no-store", credentials: "include" });
    return response.ok;
  } catch {
    return false;
  }
}

export async function authLogin(payload: { email: string; password: string; rememberMe?: boolean }): Promise<AuthSessionResponse> {
  return postJson<AuthSessionResponse>("/auth/login", payload);
}

export async function authRegister(payload: { name: string; email: string; password: string }): Promise<AuthSessionResponse> {
  return postJson<AuthSessionResponse>("/auth/register", payload);
}

export async function authMe(): Promise<AuthUser> {
  return getJson<AuthUser>("/auth/me");
}

export async function authUpdateProfile(payload: { name: string; email: string }): Promise<AuthUser> {
  const response = await fetch(`${API_BASE}/auth/me`, {
    method: "PUT",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function authLogout(): Promise<{ message: string }> {
  return postJson<{ message: string }>("/auth/logout", {});
}

export async function authForgotPassword(payload: { email: string }): Promise<AuthPasswordResetResponse> {
  return postJson<AuthPasswordResetResponse>("/auth/password/forgot", payload);
}

export async function authResetPassword(payload: { token: string; newPassword: string }): Promise<AuthSessionResponse> {
  return postJson<AuthSessionResponse>("/auth/password/reset", payload);
}

export async function authChangePassword(payload: { currentPassword: string; newPassword: string }): Promise<AuthSessionResponse> {
  return postJson<AuthSessionResponse>("/auth/password/change", payload);
}

export async function listUsers(): Promise<AuthUser[]> {
  return getJson<AuthUser[]>("/auth/users");
}

export async function getUser(id: string): Promise<AuthUser> {
  return getJson<AuthUser>(`/auth/users/${id}`);
}

export async function submitFeedback(payload: { type: string; subject: string; message: string }): Promise<FeedbackSubmissionResponse> {
  return postJson<FeedbackSubmissionResponse>("/feedback", payload);
}

export async function listFeedback(): Promise<FeedbackRecord[]> {
  return getJson<FeedbackRecord[]>("/feedback");
}

export async function updateFeedbackStatus(id: string, status: FeedbackStatus): Promise<FeedbackRecord> {
  return patchJson<FeedbackRecord>(`/feedback/${id}/status`, { status });
}

export async function updateFeedback(id: string, payload: FeedbackUpdatePayload): Promise<FeedbackRecord> {
  return patchJson<FeedbackRecord>(`/feedback/${id}`, payload);
}

export async function deleteFeedback(id: string): Promise<void> {
  await deleteJson<void>(`/feedback/${id}`);
}

async function readError(response: Response): Promise<string> {
  try {
    const body = await response.json();
    return body.detail ?? body.message ?? `API istegi basarisiz: ${response.status}`;
  } catch {
    return `API istegi basarisiz: ${response.status}`;
  }
}
