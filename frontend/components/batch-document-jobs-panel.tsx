"use client";

import { ChangeEvent, FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  AlertCircle,
  CalendarClock,
  CheckCircle2,
  Clock3,
  FolderOpen,
  LoaderCircle,
  Play,
  Plus,
  RefreshCw,
  Trash2,
  X
} from "lucide-react";
import {
  createBatchDocumentJob,
  deleteBatchDocumentJob,
  getBatchDocumentRun,
  listBatchDocumentJobs,
  listBatchDocumentRuns,
  triggerBatchDocumentJob,
  updateBatchDocumentJob,
  type BatchDocumentJob,
  type BatchDocumentJobPayload,
  type BatchDocumentRun,
  type BatchScheduleType
} from "@/lib/api";
import { formatMessage, getMessages, type Locale } from "@/lib/i18n";

type BatchDocumentJobsPanelProps = {
  locale: Locale;
};

const EMPTY_FORM: BatchDocumentJobPayload = {
  sourceType: "DIRECTORY",
  directoryPath: "",
  scheduleType: "DAILY",
  scheduledTime: "09:00",
  scheduledDate: null,
  dayOfWeek: 1,
  dayOfMonth: 1,
  enabled: true
};

function folderNameFromPath(directoryPath: string) {
  const normalized = directoryPath.replace(/[\\/]+$/, "");
  const parts = normalized.split(/[\\/]/).filter(Boolean);
  return parts[parts.length - 1] ?? "batch";
}

function generateAutoJobName(form: BatchDocumentJobPayload) {
  const prefix = folderNameFromPath(form.directoryPath ?? "");
  const schedule =
    form.scheduleType === "ONCE"
      ? "tek"
      : form.scheduleType === "DAILY"
        ? "gunluk"
        : form.scheduleType === "WEEKLY"
          ? "haftalik"
          : "aylik";
  const time = form.scheduledTime.replace(":", "-");
  let suffix = "";
  if (form.scheduleType === "ONCE" && form.scheduledDate) {
    suffix = form.scheduledDate;
  } else if (form.scheduleType === "WEEKLY" && form.dayOfWeek) {
    suffix = `gun${form.dayOfWeek}`;
  } else if (form.scheduleType === "MONTHLY" && form.dayOfMonth) {
    suffix = `ay${form.dayOfMonth}`;
  }
  const raw = suffix ? `${prefix}-${schedule}-${suffix}-${time}` : `${prefix}-${schedule}-${time}`;
  return raw
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-|-$/g, "");
}

function formatDateTime(value: string | null | undefined, locale: Locale, fallback: string) {
  if (!value) return fallback;
  return new Date(value).toLocaleString(locale === "en" ? "en-US" : "tr-TR");
}

function formatBytes(bytes: number | null | undefined) {
  if (bytes == null) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function statusClass(status: string) {
  if (status === "COMPLETED" || status === "SUCCESS") return "status-pill success";
  if (status === "PARTIAL") return "status-pill warning";
  if (status === "RUNNING") return "status-pill info";
  return "status-pill danger";
}

export function BatchDocumentJobsPanel({ locale }: BatchDocumentJobsPanelProps) {
  const t = getMessages(locale).batchDocuments;
  const [jobs, setJobs] = useState<BatchDocumentJob[]>([]);
  const [runs, setRuns] = useState<BatchDocumentRun[]>([]);
  const [selectedRun, setSelectedRun] = useState<BatchDocumentRun | null>(null);
  const [editingJobId, setEditingJobId] = useState<number | null>(null);
  const [form, setForm] = useState<BatchDocumentJobPayload>(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [runningJobId, setRunningJobId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const folderInputRef = useRef<HTMLInputElement>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [allJobs, nextRuns] = await Promise.all([listBatchDocumentJobs(), listBatchDocumentRuns(undefined, 30)]);
      setJobs(allJobs.filter((job) => job.sourceType !== "PRECEDENT"));
      setRuns(nextRuns);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t.errors.loadFailed);
    } finally {
      setLoading(false);
    }
  }, [t.errors.loadFailed]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const scheduleLabels = useMemo<Record<BatchScheduleType, string>>(
    () => ({
      ONCE: t.schedule.once,
      DAILY: t.schedule.daily,
      WEEKLY: t.schedule.weekly,
      MONTHLY: t.schedule.monthly
    }),
    [t.schedule]
  );

  const weekDays = useMemo(
    () => [
      { value: 1, label: t.weekdays.monday },
      { value: 2, label: t.weekdays.tuesday },
      { value: 3, label: t.weekdays.wednesday },
      { value: 4, label: t.weekdays.thursday },
      { value: 5, label: t.weekdays.friday },
      { value: 6, label: t.weekdays.saturday },
      { value: 7, label: t.weekdays.sunday }
    ],
    [t.weekdays]
  );

  const jobColumns = useMemo<GridColDef<BatchDocumentJob>[]>(
    () => [
      { field: "name", headerName: t.columns.name, flex: 1, minWidth: 130 },
      {
        field: "directoryPath",
        headerName: t.columns.directory,
        flex: 1.3,
        minWidth: 180,
        valueGetter: (_value, row) => row.directoryPath ?? "-"
      },
      {
        field: "scheduleType",
        headerName: t.columns.schedule,
        width: 100,
        valueGetter: (_value, row) => scheduleLabels[row.scheduleType]
      },
      {
        field: "scheduledTime",
        headerName: t.columns.time,
        width: 80,
        valueGetter: (_value, row) => row.scheduledTime?.slice(0, 5) ?? "-"
      },
      {
        field: "enabled",
        headerName: t.columns.enabled,
        width: 80,
        valueGetter: (_value, row) => (row.enabled ? t.enabledYes : t.enabledNo)
      },
      {
        field: "nextRunAt",
        headerName: t.columns.nextRun,
        flex: 1,
        minWidth: 140,
        valueGetter: (_value, row) => formatDateTime(row.nextRunAt, locale, t.notScheduled)
      }
    ],
    [locale, scheduleLabels, t]
  );

  const runColumns = useMemo<GridColDef<BatchDocumentRun>[]>(
    () => [
      { field: "jobName", headerName: t.columns.job, flex: 1, minWidth: 130 },
      {
        field: "triggerType",
        headerName: t.columns.trigger,
        width: 100,
        valueGetter: (_value, row) => (row.triggerType === "MANUAL" ? t.trigger.manual : t.trigger.scheduled)
      },
      {
        field: "status",
        headerName: t.columns.status,
        width: 110,
        renderCell: (params) => <span className={statusClass(String(params.value))}>{String(params.value)}</span>
      },
      {
        field: "startedAt",
        headerName: t.columns.startedAt,
        flex: 1,
        minWidth: 140,
        valueGetter: (_value, row) => formatDateTime(row.startedAt, locale, "-")
      },
      {
        field: "summary",
        headerName: t.columns.summary,
        flex: 1.4,
        minWidth: 200,
        valueGetter: (_value, row) =>
          row.summaryMessage ??
          formatMessage(t.runSummaryFallback, {
            success: row.successCount,
            failed: row.failedCount,
            total: row.totalFiles
          })
      }
    ],
    [locale, t]
  );

  function resetForm() {
    setEditingJobId(null);
    setForm(EMPTY_FORM);
  }

  const autoJobName = useMemo(() => (form.directoryPath ? generateAutoJobName(form) : ""), [form]);

  function pickDirectoryManually() {
    setError("");
    folderInputRef.current?.click();
  }

  function handleNativeFolderPick(event: ChangeEvent<HTMLInputElement>) {
    const files = event.target.files;
    if (!files?.length) {
      return;
    }
    const first = files[0] as File & { path?: string };
    let pickedPath = "";
    if (typeof first.path === "string" && first.path.trim()) {
      pickedPath = first.path.replace(/[\\/][^\\/]+$/, "");
    } else if (first.webkitRelativePath) {
      pickedPath = first.webkitRelativePath.split("/")[0] ?? "";
    }
    if (pickedPath) {
      setForm((current) => ({ ...current, directoryPath: pickedPath }));
      setSuccess("");
      setError("");
    }
    event.target.value = "";
  }

  function startEdit(job: BatchDocumentJob) {
    setEditingJobId(job.id);
    setForm({
      sourceType: "DIRECTORY",
      directoryPath: job.directoryPath ?? "",
      scheduleType: job.scheduleType,
      scheduledTime: job.scheduledTime?.slice(0, 5) ?? "09:00",
      scheduledDate: job.scheduledDate ?? null,
      dayOfWeek: job.dayOfWeek ?? 1,
      dayOfMonth: job.dayOfMonth ?? 1,
      enabled: job.enabled
    });
    setSuccess("");
    setError("");
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.directoryPath?.trim()) {
      setError(t.errors.directoryRequired);
      return;
    }
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const payload: BatchDocumentJobPayload = {
        ...form,
        sourceType: "DIRECTORY",
        directoryPath: form.directoryPath.trim(),
        scheduledDate: form.scheduleType === "ONCE" ? form.scheduledDate : null,
        dayOfWeek: form.scheduleType === "WEEKLY" ? form.dayOfWeek : null,
        dayOfMonth: form.scheduleType === "MONTHLY" ? form.dayOfMonth : null
      };
      if (editingJobId == null) {
        await createBatchDocumentJob(payload);
        setSuccess(t.savedCreate);
      } else {
        await updateBatchDocumentJob(editingJobId, payload);
        setSuccess(t.savedUpdate);
      }
      resetForm();
      await loadData();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : t.errors.saveFailed);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(job: BatchDocumentJob) {
    if (!window.confirm(formatMessage(t.confirmDelete, { name: job.name }))) {
      return;
    }
    setError("");
    setSuccess("");
    try {
      await deleteBatchDocumentJob(job.id);
      if (editingJobId === job.id) {
        resetForm();
      }
      setSuccess(t.deleted);
      await loadData();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t.errors.deleteFailed);
    }
  }

  async function handleRun(job: BatchDocumentJob) {
    setRunningJobId(job.id);
    setError("");
    setSuccess("");
    try {
      const run = await triggerBatchDocumentJob(job.id);
      setSelectedRun(run);
      setSuccess(run.summaryMessage ?? formatMessage(t.runCompleted, { success: run.successCount, total: run.totalFiles }));
      await loadData();
    } catch (runError) {
      setError(runError instanceof Error ? runError.message : t.errors.runFailed);
    } finally {
      setRunningJobId(null);
    }
  }

  async function openRun(run: BatchDocumentRun) {
    setError("");
    try {
      const detail = await getBatchDocumentRun(run.id);
      setSelectedRun(detail);
    } catch (detailError) {
      setError(detailError instanceof Error ? detailError.message : t.errors.loadFailed);
    }
  }

  return (
    <article className="panel admin-card batch-documents-panel">
      <div className="section-head">
        <div>
          <span className="section-label">{t.eyebrow}</span>
          <h3>{t.title}</h3>
          <p className="section-copy">{t.subtitle}</p>
        </div>
        <div className="admin-actions">
          <button className="secondary-button" type="button" onClick={() => void loadData()} disabled={loading}>
            {loading ? <LoaderCircle className="spin" size={17} /> : <RefreshCw size={17} />}
            {t.refresh}
          </button>
          <button className="secondary-button" type="button" onClick={resetForm}>
            <Plus size={17} />
            {t.newJob}
          </button>
        </div>
      </div>

      {error ? (
        <div className="inline-error">
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      ) : null}
      {success ? (
        <div className="success-banner">
          <CheckCircle2 size={20} />
          <span>{success}</span>
        </div>
      ) : null}

      <div className="batch-documents-layout">
        <form className="batch-job-form" onSubmit={(event) => void handleSubmit(event)}>
          <div className="batch-form-header">
            <h4>{editingJobId == null ? t.form.createTitle : t.form.editTitle}</h4>
            {autoJobName ? (
              <span className="batch-form-job-name">
                {t.form.autoName}: <strong>{autoJobName}</strong>
              </span>
            ) : null}
          </div>

          <div className="batch-form-section">
            <label className="field-label">
              {t.form.directory}
              <div className="batch-directory-picker">
                <input
                  required
                  value={form.directoryPath ?? ""}
                  placeholder={t.form.directoryPlaceholder}
                  spellCheck={false}
                  onChange={(event) => setForm((current) => ({ ...current, directoryPath: event.target.value }))}
                />
                <button className="secondary-button" type="button" onClick={pickDirectoryManually}>
                  <FolderOpen size={17} />
                  {t.form.browseDirectory}
                </button>
                <input
                  ref={folderInputRef}
                  hidden
                  type="file"
                  // @ts-expect-error webkitdirectory is supported by Chromium-based browsers
                  webkitdirectory=""
                  directory=""
                  multiple
                  onChange={handleNativeFolderPick}
                />
              </div>
              <small className="field-hint">{t.form.directoryManualHint}</small>
            </label>
          </div>

          <div className="batch-form-section">
            <div className="batch-form-section-label">
              <CalendarClock size={15} />
              {t.form.scheduleSection}
            </div>
            <div className="batch-form-grid">
              <label className="field-label">
                {t.form.scheduleType}
                <select
                  value={form.scheduleType}
                  onChange={(event) => setForm((current) => ({ ...current, scheduleType: event.target.value as BatchScheduleType }))}
                >
                  <option value="ONCE">{t.schedule.once}</option>
                  <option value="DAILY">{t.schedule.daily}</option>
                  <option value="WEEKLY">{t.schedule.weekly}</option>
                  <option value="MONTHLY">{t.schedule.monthly}</option>
                </select>
              </label>
              <label className="field-label">
                {t.form.time}
                <input
                  required
                  type="time"
                  value={form.scheduledTime}
                  onChange={(event) => setForm((current) => ({ ...current, scheduledTime: event.target.value }))}
                />
              </label>
              {form.scheduleType === "ONCE" ? (
                <label className="field-label">
                  {t.form.date}
                  <input
                    required
                    type="date"
                    value={form.scheduledDate ?? ""}
                    onChange={(event) => setForm((current) => ({ ...current, scheduledDate: event.target.value || null }))}
                  />
                </label>
              ) : null}
              {form.scheduleType === "WEEKLY" ? (
                <label className="field-label">
                  {t.form.weekday}
                  <select
                    value={form.dayOfWeek ?? 1}
                    onChange={(event) => setForm((current) => ({ ...current, dayOfWeek: Number(event.target.value) }))}
                  >
                    {weekDays.map((day) => (
                      <option key={day.value} value={day.value}>
                        {day.label}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
              {form.scheduleType === "MONTHLY" ? (
                <label className="field-label">
                  {t.form.monthDay}
                  <input
                    required
                    type="number"
                    min={1}
                    max={31}
                    value={form.dayOfMonth ?? 1}
                    onChange={(event) => setForm((current) => ({ ...current, dayOfMonth: Number(event.target.value) }))}
                  />
                </label>
              ) : null}
            </div>
            <label className="setting-row compact">
              <div>
                <strong>{t.form.enabled}</strong>
                <span>{t.form.enabledHint}</span>
              </div>
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(event) => setForm((current) => ({ ...current, enabled: event.target.checked }))}
              />
            </label>
          </div>

          <div className="batch-form-actions">
            <button disabled={saving} type="submit">
              {saving ? <LoaderCircle className="spin" size={17} /> : <FolderOpen size={17} />}
              {editingJobId == null ? t.form.saveCreate : t.form.saveUpdate}
            </button>
            {editingJobId != null ? (
              <button className="secondary-button" type="button" onClick={resetForm}>
                {t.form.cancelEdit}
              </button>
            ) : null}
          </div>
        </form>

        <section className="batch-jobs-section">
          <div className="batch-section-title">
            <h4>{t.jobsSectionTitle}</h4>
            <span className="status">{jobs.length} {t.jobsCount}</span>
          </div>
          <div className="feedback-datagrid-wrap">
            <DataGrid
              autoHeight
              rows={jobs}
              columns={[
                ...jobColumns,
                {
                  field: "actions",
                  headerName: t.columns.actions,
                  width: 150,
                  sortable: false,
                  filterable: false,
                  renderCell: (params) => (
                    <div className="table-actions">
                      <button className="secondary-button compact" type="button" onClick={() => startEdit(params.row)}>
                        {t.actions.edit}
                      </button>
                      <button
                        className="secondary-button compact"
                        type="button"
                        disabled={runningJobId === params.row.id}
                        onClick={() => void handleRun(params.row)}
                      >
                        {runningJobId === params.row.id ? <LoaderCircle className="spin" size={15} /> : <Play size={15} />}
                      </button>
                      <button className="secondary-button compact danger" type="button" onClick={() => void handleDelete(params.row)}>
                        <Trash2 size={15} />
                      </button>
                    </div>
                  )
                }
              ]}
              getRowId={(row) => row.id}
              disableRowSelectionOnClick
              initialState={{ pagination: { paginationModel: { page: 0, pageSize: 5 } } }}
              pageSizeOptions={[5, 10]}
              rowHeight={52}
              columnHeaderHeight={40}
              loading={loading}
            />
          </div>
        </section>
      </div>

      <section className="batch-history-section">
        <div className="batch-section-title">
          <div>
            <span className="section-label">{t.history.eyebrow}</span>
            <h4>{t.history.title}</h4>
          </div>
          <span className="status">
            <Clock3 size={14} /> {runs.length}
          </span>
        </div>
        <div className="feedback-datagrid-wrap">
          <DataGrid
            autoHeight
            rows={runs}
            columns={runColumns}
            getRowId={(row) => row.id}
            disableRowSelectionOnClick
            onRowClick={(params) => void openRun(params.row)}
            initialState={{ pagination: { paginationModel: { page: 0, pageSize: 6 } } }}
            pageSizeOptions={[6, 12]}
            rowHeight={48}
            columnHeaderHeight={40}
            loading={loading}
          />
        </div>
      </section>

      {selectedRun ? (
        <section className="batch-run-detail">
          <div className="batch-run-detail-head">
            <div>
              <span className="section-label">{t.runDetail.eyebrow}</span>
              <h4>{selectedRun.jobName}</h4>
              <p className="section-copy">{selectedRun.summaryMessage}</p>
            </div>
            <div className="batch-run-detail-actions">
              <span className={statusClass(selectedRun.status)}>{selectedRun.status}</span>
              <button className="secondary-button compact icon-only" type="button" onClick={() => setSelectedRun(null)} title={t.runDetail.close}>
                <X size={16} />
              </button>
            </div>
          </div>
          <div className="batch-run-stats">
            <div className="batch-run-stat">
              <span>{t.runDetail.total}</span>
              <strong>{selectedRun.totalFiles}</strong>
            </div>
            <div className="batch-run-stat success">
              <span>{t.runDetail.success}</span>
              <strong>{selectedRun.successCount}</strong>
            </div>
            <div className="batch-run-stat danger">
              <span>{t.runDetail.failed}</span>
              <strong>{selectedRun.failedCount}</strong>
            </div>
            <div className="batch-run-stat">
              <span>{t.runDetail.skipped}</span>
              <strong>{selectedRun.skippedCount}</strong>
            </div>
          </div>
          <div className="feedback-datagrid-wrap">
            <DataGrid
              autoHeight
              rows={selectedRun.files}
              columns={[
                { field: "filename", headerName: t.fileColumns.name, flex: 1, minWidth: 160 },
                {
                  field: "fileSizeBytes",
                  headerName: t.fileColumns.size,
                  width: 90,
                  valueGetter: (_value, row) => formatBytes(row.fileSizeBytes)
                },
                {
                  field: "status",
                  headerName: t.fileColumns.status,
                  width: 100,
                  renderCell: (params) => <span className={statusClass(String(params.value))}>{String(params.value)}</span>
                },
                {
                  field: "documentId",
                  headerName: t.fileColumns.documentId,
                  width: 100,
                  valueGetter: (_value, row) => row.documentId ?? "-"
                },
                {
                  field: "errorMessage",
                  headerName: t.fileColumns.error,
                  flex: 1.2,
                  minWidth: 160,
                  valueGetter: (_value, row) => row.errorMessage ?? "-"
                }
              ]}
              getRowId={(row) => row.id}
              disableRowSelectionOnClick
              initialState={{ pagination: { paginationModel: { page: 0, pageSize: 8 } } }}
              pageSizeOptions={[8, 20]}
              rowHeight={44}
              columnHeaderHeight={40}
            />
          </div>
        </section>
      ) : null}
    </article>
  );
}
