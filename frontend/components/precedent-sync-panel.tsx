"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  AlertCircle,
  CheckCircle2,
  Clock3,
  Gavel,
  LoaderCircle,
  Play,
  Plus,
  RefreshCw,
  Trash2,
  X
} from "lucide-react";
import {
  createPrecedentSyncTask,
  deletePrecedentSyncTask,
  getPrecedentSyncRun,
  listPrecedentSyncRuns,
  listPrecedentSyncTasks,
  triggerPrecedentSyncTask,
  updatePrecedentSyncTask,
  type PrecedentCourtType,
  type PrecedentSyncRun,
  type PrecedentSyncTask,
  type PrecedentSyncTaskPayload
} from "@/lib/api";
import { formatMessage, getMessages, type Locale } from "@/lib/i18n";

type PrecedentSyncPanelProps = {
  locale: Locale;
};

const PRECEDENT_COURTS: { value: PrecedentCourtType; labelKey: "courtYargitay" | "courtDanistay" | "courtAnayasa" }[] = [
  { value: "YARGITAY", labelKey: "courtYargitay" },
  { value: "DANISTAY", labelKey: "courtDanistay" },
  { value: "ANAYASA", labelKey: "courtAnayasa" }
];

const EMPTY_FORM: PrecedentSyncTaskPayload = {
  courts: ["YARGITAY"],
  dateFrom: "",
  dateTo: "",
  maxDocumentsPerRun: 500,
  intervalMinutes: 60,
  enabled: true
};

function formatDateTime(value: string | null | undefined, locale: Locale, fallback: string) {
  if (!value) return fallback;
  return new Date(value).toLocaleString(locale === "en" ? "en-US" : "tr-TR");
}

function formatDate(value: string | null | undefined, locale: Locale) {
  if (!value) return "-";
  return new Date(value).toLocaleDateString(locale === "en" ? "en-US" : "tr-TR");
}

function statusClass(status: string) {
  if (status === "COMPLETED" || status === "SUCCESS") return "status-pill success";
  if (status === "PARTIAL") return "status-pill warning";
  if (status === "RUNNING" || status === "IDLE") return "status-pill info";
  return "status-pill danger";
}

export function PrecedentSyncPanel({ locale }: PrecedentSyncPanelProps) {
  const t = getMessages(locale).precedentSync;
  const [tasks, setTasks] = useState<PrecedentSyncTask[]>([]);
  const [runs, setRuns] = useState<PrecedentSyncRun[]>([]);
  const [selectedRun, setSelectedRun] = useState<PrecedentSyncRun | null>(null);
  const [editingTaskId, setEditingTaskId] = useState<number | null>(null);
  const [form, setForm] = useState<PrecedentSyncTaskPayload>(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [runningTaskId, setRunningTaskId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [nextTasks, nextRuns] = await Promise.all([listPrecedentSyncTasks(), listPrecedentSyncRuns(undefined, 30)]);
      setTasks(nextTasks);
      setRuns(nextRuns);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t.errors.loadFailed);
    } finally {
      setLoading(false);
    }
  }, [t.errors.loadFailed]);

  useEffect(() => {
    void loadData();
    const timer = window.setInterval(() => {
      void loadData();
    }, 60_000);
    return () => window.clearInterval(timer);
  }, [loadData]);

  const taskColumns = useMemo<GridColDef<PrecedentSyncTask>[]>(
    () => [
      { field: "name", headerName: t.columns.name, flex: 1, minWidth: 130 },
      {
        field: "courts",
        headerName: t.columns.courts,
        width: 140,
        valueGetter: (_value, row) => row.courts?.join(", ") ?? "-"
      },
      {
        field: "dateRange",
        headerName: t.columns.dateRange,
        flex: 1,
        minWidth: 170,
        valueGetter: (_value, row) => `${formatDate(row.dateFrom, locale)} – ${formatDate(row.dateTo, locale)}`
      },
      {
        field: "intervalMinutes",
        headerName: t.columns.interval,
        width: 100,
        valueGetter: (_value, row) => formatMessage(t.intervalValue, { minutes: row.intervalMinutes })
      },
      {
        field: "status",
        headerName: t.columns.status,
        width: 100,
        renderCell: (params) => (
          <span className={statusClass(String(params.value))}>
            {params.value === "RUNNING" ? t.status.running : params.row.enabled ? t.status.active : t.status.paused}
          </span>
        )
      },
      {
        field: "nextRunAt",
        headerName: t.columns.nextRun,
        flex: 1,
        minWidth: 140,
        valueGetter: (_value, row) =>
          row.enabled ? formatDateTime(row.nextRunAt, locale, t.notScheduled) : t.status.paused
      }
    ],
    [locale, t]
  );

  const runColumns = useMemo<GridColDef<PrecedentSyncRun>[]>(
    () => [
      { field: "taskName", headerName: t.columns.task, flex: 1, minWidth: 130 },
      {
        field: "triggerType",
        headerName: t.columns.trigger,
        width: 100,
        valueGetter: (_value, row) => (row.triggerType === "MANUAL" ? t.trigger.manual : t.trigger.scheduled)
      },
      {
        field: "status",
        headerName: t.columns.runStatus,
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
    setEditingTaskId(null);
    setForm(EMPTY_FORM);
  }

  function startEdit(task: PrecedentSyncTask) {
    setEditingTaskId(task.id);
    setForm({
      courts: task.courts ?? [],
      dateFrom: task.dateFrom,
      dateTo: task.dateTo,
      maxDocumentsPerRun: task.maxDocumentsPerRun,
      intervalMinutes: task.intervalMinutes,
      enabled: task.enabled
    });
    setSuccess("");
    setError("");
  }

  function toggleCourt(court: PrecedentCourtType) {
    setForm((current) => {
      const selected = current.courts ?? [];
      const next = selected.includes(court) ? selected.filter((item) => item !== court) : [...selected, court];
      return { ...current, courts: next };
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.courts?.length) {
      setError(t.errors.courtsRequired);
      return;
    }
    if (!form.dateFrom || !form.dateTo) {
      setError(t.errors.dateRangeRequired);
      return;
    }
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      if (editingTaskId == null) {
        await createPrecedentSyncTask(form);
        setSuccess(t.savedCreate);
      } else {
        await updatePrecedentSyncTask(editingTaskId, form);
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

  async function handleDelete(task: PrecedentSyncTask) {
    if (!window.confirm(formatMessage(t.confirmDelete, { name: task.name }))) {
      return;
    }
    setError("");
    setSuccess("");
    try {
      await deletePrecedentSyncTask(task.id);
      if (editingTaskId === task.id) {
        resetForm();
      }
      setSuccess(t.deleted);
      await loadData();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t.errors.deleteFailed);
    }
  }

  async function handleRun(task: PrecedentSyncTask) {
    setRunningTaskId(task.id);
    setError("");
    setSuccess("");
    try {
      const run = await triggerPrecedentSyncTask(task.id);
      setSelectedRun(run);
      setSuccess(run.summaryMessage ?? formatMessage(t.runCompleted, { success: run.successCount, total: run.totalFiles }));
      await loadData();
    } catch (runError) {
      setError(runError instanceof Error ? runError.message : t.errors.runFailed);
    } finally {
      setRunningTaskId(null);
    }
  }

  async function openRun(run: PrecedentSyncRun) {
    setError("");
    try {
      const detail = await getPrecedentSyncRun(run.id);
      setSelectedRun(detail);
    } catch (detailError) {
      setError(detailError instanceof Error ? detailError.message : t.errors.loadFailed);
    }
  }

  return (
    <article className="panel admin-card precedent-sync-panel">
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
            {t.newTask}
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

      <div className="precedent-sync-layout">
        <form className="precedent-sync-form" onSubmit={(event) => void handleSubmit(event)}>
          <h4>{editingTaskId == null ? t.form.createTitle : t.form.editTitle}</h4>

          <div className="batch-form-section-label">{t.form.courts}</div>
          <div className="batch-court-chips">
            {PRECEDENT_COURTS.map((court) => {
              const active = form.courts?.includes(court.value) ?? false;
              return (
                <button
                  key={court.value}
                  className={`batch-court-chip${active ? " active" : ""}`}
                  type="button"
                  onClick={() => toggleCourt(court.value)}
                >
                  {t.form[court.labelKey]}
                </button>
              );
            })}
          </div>

          <div className="batch-form-grid">
            <label className="field-label">
              {t.form.dateFrom}
              <input
                required
                type="date"
                value={form.dateFrom}
                onChange={(event) => setForm((current) => ({ ...current, dateFrom: event.target.value }))}
              />
            </label>
            <label className="field-label">
              {t.form.dateTo}
              <input
                required
                type="date"
                value={form.dateTo}
                onChange={(event) => setForm((current) => ({ ...current, dateTo: event.target.value }))}
              />
            </label>
            <label className="field-label">
              {t.form.maxDocumentsPerRun}
              <input
                required
                type="number"
                min={1}
                max={5000}
                value={form.maxDocumentsPerRun ?? 500}
                onChange={(event) => setForm((current) => ({ ...current, maxDocumentsPerRun: Number(event.target.value) }))}
              />
            </label>
            <label className="field-label">
              {t.form.intervalMinutes}
              <input
                required
                type="number"
                min={5}
                max={1440}
                value={form.intervalMinutes ?? 60}
                onChange={(event) => setForm((current) => ({ ...current, intervalMinutes: Number(event.target.value) }))}
              />
            </label>
          </div>
          <small className="field-hint">{t.form.hint}</small>

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

          <div className="batch-form-actions">
            <button disabled={saving} type="submit">
              {saving ? <LoaderCircle className="spin" size={17} /> : <Gavel size={17} />}
              {editingTaskId == null ? t.form.saveCreate : t.form.saveUpdate}
            </button>
            {editingTaskId != null ? (
              <button className="secondary-button" type="button" onClick={resetForm}>
                {t.form.cancelEdit}
              </button>
            ) : null}
          </div>
        </form>

        <section className="precedent-sync-tasks-section">
          <div className="batch-section-title">
            <h4>{t.tasksSectionTitle}</h4>
            <span className="status">{tasks.length} {t.tasksCount}</span>
          </div>
          <div className="feedback-datagrid-wrap">
            <DataGrid
              autoHeight
              rows={tasks}
              columns={[
                ...taskColumns,
                {
                  field: "actions",
                  headerName: t.columns.actions,
                  width: 140,
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
                        disabled={runningTaskId === params.row.id}
                        onClick={() => void handleRun(params.row)}
                      >
                        {runningTaskId === params.row.id ? <LoaderCircle className="spin" size={15} /> : <Play size={15} />}
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
          <h4>{t.history.title}</h4>
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
              <h4>{selectedRun.taskName}</h4>
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
                  field: "chunkCount",
                  headerName: t.fileColumns.chunks,
                  width: 80,
                  valueGetter: (_value, row) => row.chunkCount ?? "-"
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
