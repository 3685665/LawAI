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
  stripeProductId?: string | null;
  stripeMonthlyPriceId?: string | null;
  stripeYearlyPriceId?: string | null;
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
  stripeProductId?: string;
  stripeMonthlyPriceId?: string;
  stripeYearlyPriceId?: string;
};
export type BillingCheckoutResponse = {
  checkoutUrl: string;
  checkoutSessionId: string;
  subscription: UserSubscription;
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
