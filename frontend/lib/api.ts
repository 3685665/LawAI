export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080/api";

export type Precedent = {
  sourceId?: string | null;
  court: string;
  chamber?: string | null;
  docketNo?: string | null;
  decisionNo?: string | null;
  date?: string | null;
  topic: string;
  summary: string;
  content?: string | null;
  outcome?: string | null;
};

export type PrecedentSummarizeResponse = {
  summary: string;
  disclaimer?: string | null;
};

export type PrecedentSummarizePayload = {
  court: string;
  chamber?: string | null;
  docketNo?: string | null;
  decisionNo?: string | null;
  date?: string | null;
  topic: string;
  summary?: string | null;
  content: string;
};

export type PetitionCaseContextPayload = {
  caseId?: string | null;
  caseType?: string | null;
  caseLabel?: string | null;
  clientName?: string | null;
  opponentName?: string | null;
  courtName?: string | null;
  subject?: string | null;
  summary?: string | null;
  petitionType?: string | null;
  petitionFacts?: string | null;
  petitionDemands?: string | null;
};

export type PrecedentApplyPayload = PrecedentSummarizePayload & {
  aiSummary?: string | null;
  caseContext: PetitionCaseContextPayload;
};

export type PrecedentApplyResponse = {
  applicationNote: string;
  legalGroundsSnippet: string;
  factsLinkSnippet: string;
  citationLine: string;
  disclaimer?: string | null;
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
  verified?: boolean | null;
  verifiedAt?: string | null;
};

export type AdminUserPayload = {
  name: string;
  email: string;
  role: "USER" | "ADMIN";
  verified?: boolean;
  password?: string;
};

export type AuthSessionResponse = { user: AuthUser };
export type AuthRegisterResponse = { message: string; verificationTokenPreview?: string | null; expiresAt?: string | null; verificationLinkPreview?: string | null };
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
export type BatchScheduleType = "ONCE" | "DAILY" | "WEEKLY" | "MONTHLY";

export type BatchSourceType = "DIRECTORY" | "PRECEDENT";
export type PrecedentCourtType = "YARGITAY" | "DANISTAY" | "ANAYASA";

export type BatchDocumentJob = {
  id: number;
  name: string;
  sourceType: BatchSourceType;
  directoryPath?: string | null;
  precedentCourts?: PrecedentCourtType[];
  precedentQuery?: string | null;
  precedentDateFrom?: string | null;
  precedentDateTo?: string | null;
  precedentMaxDocuments?: number | null;
  scheduleType: BatchScheduleType;
  scheduledTime: string;
  scheduledDate?: string | null;
  dayOfWeek?: number | null;
  dayOfMonth?: number | null;
  enabled: boolean;
  createdByUserId?: string | null;
  createdByUserName?: string | null;
  lastRunAt?: string | null;
  nextRunAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type BatchDocumentRunFile = {
  id: number;
  filename: string;
  filePath: string;
  fileSizeBytes?: number | null;
  status: "SUCCESS" | "FAILED" | "SKIPPED";
  documentId?: number | null;
  extractedChars?: number | null;
  chunkCount?: number | null;
  errorMessage?: string | null;
  processedAt: string;
};

export type BatchDocumentRun = {
  id: number;
  jobId: number;
  jobName: string;
  triggerType: "SCHEDULED" | "MANUAL";
  status: "RUNNING" | "COMPLETED" | "PARTIAL" | "FAILED";
  startedAt: string;
  finishedAt?: string | null;
  totalFiles: number;
  successCount: number;
  failedCount: number;
  skippedCount: number;
  summaryMessage?: string | null;
  files: BatchDocumentRunFile[];
};

export type DirectoryBrowseEntry = {
  name: string;
  path: string;
  directory: boolean;
};

export type DirectoryBrowseResponse = {
  currentPath?: string | null;
  parentPath?: string | null;
  roots: string[];
  entries: DirectoryBrowseEntry[];
};

export type BatchDocumentJobPayload = {
  name?: string | null;
  sourceType?: BatchSourceType;
  directoryPath?: string | null;
  precedentCourts?: PrecedentCourtType[];
  precedentQuery?: string | null;
  precedentDateFrom?: string | null;
  precedentDateTo?: string | null;
  precedentMaxDocuments?: number | null;
  scheduleType: BatchScheduleType;
  scheduledTime: string;
  scheduledDate?: string | null;
  dayOfWeek?: number | null;
  dayOfMonth?: number | null;
  enabled: boolean;
};

export type PrecedentSyncTask = {
  id: number;
  name: string;
  courts: PrecedentCourtType[];
  dateFrom: string;
  dateTo: string;
  maxDocumentsPerRun: number;
  intervalMinutes: number;
  enabled: boolean;
  status: "IDLE" | "RUNNING";
  lastRunAt?: string | null;
  nextRunAt?: string | null;
  createdByUserId?: string | null;
  createdByUserName?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PrecedentSyncRunFile = {
  id: number;
  filename: string;
  storedPath: string;
  status: "SUCCESS" | "FAILED" | "SKIPPED";
  documentId?: number | null;
  extractedChars?: number | null;
  chunkCount?: number | null;
  errorMessage?: string | null;
  processedAt: string;
};

export type PrecedentSyncRun = {
  id: number;
  taskId: number;
  taskName: string;
  triggerType: "SCHEDULED" | "MANUAL";
  status: "RUNNING" | "COMPLETED" | "PARTIAL" | "FAILED";
  startedAt: string;
  finishedAt?: string | null;
  totalFiles: number;
  successCount: number;
  failedCount: number;
  skippedCount: number;
  summaryMessage?: string | null;
  files: PrecedentSyncRunFile[];
};

export type PrecedentSyncTaskPayload = {
  name?: string | null;
  courts: PrecedentCourtType[];
  dateFrom: string;
  dateTo: string;
  maxDocumentsPerRun?: number;
  intervalMinutes?: number;
  enabled: boolean;
};

export type ActivityLogRecord = {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  role: string;
  source: string;
  action: string;
  screen: string;
  detail: string;
  path: string;
  createdAt: string;
};
export type ChatHistoryMessage = {
  id: string;
  role: "user" | "assistant";
  text: string;
  citations?: Precedent[];
  disclaimer?: string | null;
  createdAt: string;
};
export type ChatHistorySession = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messages: ChatHistoryMessage[];
};
export type SubscriptionPlan = {
  id: string;
  name: string;
  slug: string;
  badge?: string | null;
  description?: string | null;
  monthlyPrice: number;
  yearlyPrice: number;
  currency: string;
  usageLimit?: string | null;
  usagePeriod?: string | null;
  highlighted: boolean;
  active: boolean;
  sortOrder: number;
  features: string[];
  lockedFeatures: string[];
  ctaLabel?: string | null;
  iyzicoProductRef?: string | null;
  iyzicoMonthlyPlanRef?: string | null;
  iyzicoYearlyPlanRef?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};
export type UserSubscription = {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  planId: string;
  planName: string;
  billingCycle: "monthly" | "yearly";
  status: "PENDING_PAYMENT" | "ACTIVE" | "PAUSED" | "PAST_DUE" | "CANCELLED" | "EXPIRED";
  provider?: string | null;
  providerCustomerId?: string | null;
  providerSubscriptionId?: string | null;
  providerCheckoutSessionId?: string | null;
  providerPriceId?: string | null;
  lastPaymentStatus?: string | null;
  cancelAtPeriodEnd?: boolean;
  startsAt: string;
  endsAt: string;
  createdAt: string;
  updatedAt: string;
};
export type SubscriptionPlanPayload = {
  name: string;
  slug?: string;
  badge?: string;
  description?: string;
  monthlyPrice: number;
  yearlyPrice: number;
  currency: string;
  usageLimit?: string;
  usagePeriod?: string;
  highlighted: boolean;
  active: boolean;
  sortOrder: number;
  features: string[];
  lockedFeatures: string[];
  ctaLabel?: string;
  iyzicoProductRef?: string;
  iyzicoMonthlyPlanRef?: string;
  iyzicoYearlyPlanRef?: string;
};
export type BillingCheckoutResponse = {
  checkoutUrl: string;
  checkoutSessionId: string;
  subscription: UserSubscription;
  checkoutFormContent: string;
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

export async function summarizePrecedent(payload: PrecedentSummarizePayload): Promise<PrecedentSummarizeResponse> {
  return postJson<PrecedentSummarizeResponse>("/precedents/summarize", payload);
}

export async function applyPrecedentToPetition(payload: PrecedentApplyPayload): Promise<PrecedentApplyResponse> {
  return postJson<PrecedentApplyResponse>("/precedents/apply-to-petition", payload);
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

export async function authRegister(payload: { name: string; email: string; password: string }): Promise<AuthRegisterResponse> {
  return postJson<AuthRegisterResponse>("/auth/register", payload);
}

export async function authGoogle(payload: { credential: string }): Promise<AuthSessionResponse> {
  return postJson<AuthSessionResponse>("/auth/google", payload);
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

export async function authVerify(token: string): Promise<{ message: string }> {
  return getJson<{ message: string }>(`/auth/verify?token=${encodeURIComponent(token)}`);
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

export async function createUser(payload: AdminUserPayload & { password: string }): Promise<AuthUser> {
  return postJson<AuthUser>("/auth/users", payload);
}

export async function updateUser(id: string, payload: AdminUserPayload): Promise<AuthUser> {
  const response = await fetch(`${API_BASE}/auth/users/${id}`, {
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

export async function deleteUser(id: string): Promise<void> {
  await deleteJson<void>(`/auth/users/${id}`);
}

export async function createActivityLog(payload: { action: string; screen: string; detail?: string; path?: string }): Promise<ActivityLogRecord> {
  return postJson<ActivityLogRecord>("/activity-logs", payload);
}

export async function listMyActivityLogs(): Promise<ActivityLogRecord[]> {
  return getJson<ActivityLogRecord[]>("/activity-logs/me");
}

export async function listActivityLogs(): Promise<ActivityLogRecord[]> {
  return getJson<ActivityLogRecord[]>("/activity-logs");
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

export async function browseBatchDocumentDirectories(path?: string): Promise<DirectoryBrowseResponse> {
  const query = path ? `?path=${encodeURIComponent(path)}` : "";
  return getJson<DirectoryBrowseResponse>(`/batch-documents/directories${query}`);
}

export async function listBatchDocumentJobs(): Promise<BatchDocumentJob[]> {
  return getJson<BatchDocumentJob[]>("/batch-documents/jobs");
}

export async function createBatchDocumentJob(payload: BatchDocumentJobPayload): Promise<BatchDocumentJob> {
  return postJson<BatchDocumentJob>("/batch-documents/jobs", payload);
}

export async function updateBatchDocumentJob(id: number, payload: BatchDocumentJobPayload): Promise<BatchDocumentJob> {
  const response = await fetch(`${API_BASE}/batch-documents/jobs/${id}`, {
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

export async function deleteBatchDocumentJob(id: number): Promise<void> {
  await deleteJson<void>(`/batch-documents/jobs/${id}`);
}

export async function triggerBatchDocumentJob(id: number): Promise<BatchDocumentRun> {
  return postJson<BatchDocumentRun>(`/batch-documents/jobs/${id}/run`, {});
}

export async function listBatchDocumentRuns(jobId?: number, limit = 20): Promise<BatchDocumentRun[]> {
  const query = new URLSearchParams({ limit: String(limit) });
  if (jobId != null) {
    query.set("jobId", String(jobId));
  }
  return getJson<BatchDocumentRun[]>(`/batch-documents/runs?${query.toString()}`);
}

export async function getBatchDocumentRun(runId: number): Promise<BatchDocumentRun> {
  return getJson<BatchDocumentRun>(`/batch-documents/runs/${runId}`);
}

export async function listPrecedentSyncTasks(): Promise<PrecedentSyncTask[]> {
  return getJson<PrecedentSyncTask[]>("/precedent-sync/tasks");
}

export async function createPrecedentSyncTask(payload: PrecedentSyncTaskPayload): Promise<PrecedentSyncTask> {
  return postJson<PrecedentSyncTask>("/precedent-sync/tasks", payload);
}

export async function updatePrecedentSyncTask(id: number, payload: PrecedentSyncTaskPayload): Promise<PrecedentSyncTask> {
  const response = await fetch(`${API_BASE}/precedent-sync/tasks/${id}`, {
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

export async function deletePrecedentSyncTask(id: number): Promise<void> {
  await deleteJson<void>(`/precedent-sync/tasks/${id}`);
}

export async function triggerPrecedentSyncTask(id: number): Promise<PrecedentSyncRun> {
  return postJson<PrecedentSyncRun>(`/precedent-sync/tasks/${id}/run`, {});
}

export async function listPrecedentSyncRuns(taskId?: number, limit = 20): Promise<PrecedentSyncRun[]> {
  const query = new URLSearchParams({ limit: String(limit) });
  if (taskId != null) {
    query.set("taskId", String(taskId));
  }
  return getJson<PrecedentSyncRun[]>(`/precedent-sync/runs?${query.toString()}`);
}

export async function getPrecedentSyncRun(runId: number): Promise<PrecedentSyncRun> {
  return getJson<PrecedentSyncRun>(`/precedent-sync/runs/${runId}`);
}

export async function listSubscriptions(): Promise<SubscriptionPlan[]> {
  return getJson<SubscriptionPlan[]>("/subscriptions");
}

export async function getMySubscription(): Promise<UserSubscription | null> {
  const response = await fetch(`${API_BASE}/subscriptions/me`, { cache: "no-store", credentials: "include" });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  const text = await response.text();
  return text ? JSON.parse(text) as UserSubscription : null;
}

export async function subscribeToPlan(payload: { planId: string; billingCycle: "monthly" | "yearly" }): Promise<UserSubscription> {
  return postJson<UserSubscription>("/subscriptions/me", payload);
}

export async function createBillingCheckout(payload: { planId: string; billingCycle: "monthly" | "yearly" }): Promise<BillingCheckoutResponse> {
  return postJson<BillingCheckoutResponse>("/billing/checkout", payload);
}

export async function cancelMySubscription(): Promise<UserSubscription> {
  return postJson<UserSubscription>("/subscriptions/me/cancel", {});
}

export async function listAdminSubscriptions(): Promise<SubscriptionPlan[]> {
  return getJson<SubscriptionPlan[]>("/subscriptions/admin");
}

export async function listAdminUserSubscriptions(): Promise<UserSubscription[]> {
  return getJson<UserSubscription[]>("/subscriptions/admin/users");
}

export async function createSubscriptionPlan(payload: SubscriptionPlanPayload): Promise<SubscriptionPlan> {
  return postJson<SubscriptionPlan>("/subscriptions/admin", payload);
}

export async function updateSubscriptionPlan(id: string, payload: SubscriptionPlanPayload): Promise<SubscriptionPlan> {
  const response = await fetch(`${API_BASE}/subscriptions/admin/${id}`, {
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

export async function updateUserSubscriptionStatus(id: string, status: UserSubscription["status"]): Promise<UserSubscription> {
  const response = await fetch(`${API_BASE}/subscriptions/admin/users/${id}/status`, {
    method: "PUT",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status })
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  return response.json();
}

export async function deleteSubscriptionPlan(id: string): Promise<void> {
  await deleteJson<void>(`/subscriptions/admin/${id}`);
}

async function readError(response: Response): Promise<string> {
  try {
    const body = await response.json();
    return body.detail ?? body.message ?? `API istegi basarisiz: ${response.status}`;
  } catch {
    return `API istegi basarisiz: ${response.status}`;
  }
}

export type ResearchStepStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED";

export type ResearchStep = {
  type: string;
  label: string;
  status: ResearchStepStatus;
};

export type ResearchSourceResult = {
  source: "LEGISLATION" | "CASE_LAW" | "WEB";
  findings: string[];
};

export type LegalResearchResponse = {
  plan: {
    query: string;
    sources: Array<"LEGISLATION" | "CASE_LAW" | "WEB">;
  };
  steps: ResearchStep[];
  sourceResults: ResearchSourceResult[];
  answer: string;
  disclaimer: string;
  sessionId?: string | null;
};

export type ResearchProgressEvent = {
  event: "step" | "complete";
  stepType?: string;
  label?: string;
  status?: ResearchStepStatus;
  response?: LegalResearchResponse;
};

export async function runLegalResearch(query: string, sessionId?: string | null): Promise<LegalResearchResponse> {
  return postJson<LegalResearchResponse>("/research/run", { query, sessionId: sessionId ?? null });
}

export async function streamLegalResearch(
  query: string,
  sessionId: string | null,
  onStep: (step: ResearchStep) => void
): Promise<LegalResearchResponse> {
  const response = await fetch(`${API_BASE}/research/run/stream`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream"
    },
    body: JSON.stringify({ query, sessionId })
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }
  if (!response.body) {
    throw new Error("SSE yanit akisi alinamadi.");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let completed: LegalResearchResponse | null = null;

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    buffer = consumeSseBuffer(buffer, (eventName, payload) => {
      if (eventName === "error") {
        const errorBody = JSON.parse(payload) as { detail?: string };
        throw new Error(errorBody.detail ?? "Hukuki arastirma basarisiz.");
      }
      const event = JSON.parse(payload) as ResearchProgressEvent;
      if (event.event === "step" && event.stepType && event.label && event.status) {
        onStep({ type: event.stepType, label: event.label, status: event.status });
      }
      if (event.event === "complete" && event.response) {
        completed = event.response;
      }
    });
  }

  if (!completed) {
    throw new Error("Arastirma tamamlanmadi.");
  }
  return completed;
}

function consumeSseBuffer(buffer: string, onEvent: (eventName: string, payload: string) => void): string {
  let working = buffer;
  while (true) {
    const boundary = working.indexOf("\n\n");
    if (boundary < 0) {
      return working;
    }
    const rawEvent = working.slice(0, boundary);
    working = working.slice(boundary + 2);

    let eventName = "message";
    const dataLines: string[] = [];
    for (const line of rawEvent.split("\n")) {
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trim());
      }
    }
    if (dataLines.length) {
      onEvent(eventName, dataLines.join("\n"));
    }
  }
}
