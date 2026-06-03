"use client";

import { Copy, GraduationCap, RefreshCw, Sparkles } from "lucide-react";
import { useMemo } from "react";
import { getMessages, type Locale } from "@/lib/i18n";

export type TrainingTabResponse = {
  title: string;
  subtitle: string;
  summary: string;
  audience: string[];
  modules: {
    id: string;
    title: string;
    summary: string;
    lessonPoints: string[];
    exercises: string[];
  }[];
  prompts: {
    label: string;
    prompt: string;
    expectedOutput: string;
  }[];
  checklist: {
    label: string;
    detail: string;
    required: boolean;
  }[];
  commonMistakes: string[];
  suggestedModes: string[];
};

type Props = {
  locale: Locale;
  loading: boolean;
  error: string;
  training: TrainingTabResponse | null;
  selectedModuleIndex: number;
  selectedPromptIndex: number;
  draft: string;
  onSelectModule: (index: number) => void;
  onSelectPrompt: (index: number) => void;
  onChangeDraft: (value: string) => void;
  onReload: () => void;
};

export function TrainingPanel({
  locale,
  loading,
  error,
  training,
  selectedModuleIndex,
  selectedPromptIndex,
  draft,
  onSelectModule,
  onSelectPrompt,
  onChangeDraft,
  onReload
}: Props) {
  const t = getMessages(locale).training;
  const selectedModule = training?.modules[selectedModuleIndex] ?? training?.modules[0] ?? null;
  const selectedPrompt = training?.prompts[selectedPromptIndex] ?? training?.prompts[0] ?? null;

  const checklistComplete = useMemo(
    () => training?.checklist.filter((item) => item.required).length ?? 0,
    [training]
  );

  async function copyDraft() {
    if (!draft.trim()) return;
    await navigator.clipboard.writeText(draft);
  }

  async function applyPrompt(prompt: string) {
    onChangeDraft(prompt);
    await navigator.clipboard.writeText(prompt);
  }

  return (
    <section className="training-shell">
      <div className="panel training-hero">
        <div className="training-hero-head">
          <div>
            <h2>{training?.title ?? t.fallbackTitle}</h2>
            <p>{training?.subtitle ?? t.fallbackSubtitle}</p>
          </div>
          <button className="secondary-button" onClick={onReload} type="button">
            <RefreshCw size={17} />
            {t.reload}
          </button>
        </div>
        <p className="training-summary">{training?.summary ?? t.fallbackSummary}</p>
        <div className="training-pills">
          {(training?.audience ?? t.audience).map((item) => (
            <span key={item} className="training-pill">{item}</span>
          ))}
        </div>
        <div className="training-meta">
          <div>
            <span>{t.module}</span>
            <strong>{training?.modules.length ?? 0}</strong>
          </div>
          <div>
            <span>{t.prompt}</span>
            <strong>{training?.prompts.length ?? 0}</strong>
          </div>
          <div>
            <span>{t.checkpoint}</span>
            <strong>{checklistComplete}</strong>
          </div>
        </div>
        {training?.suggestedModes?.length ? (
          <div className="training-modes">
            {training.suggestedModes.map((mode) => <span key={mode}>{mode}</span>)}
          </div>
        ) : null}
      </div>

      {error ? <div className="error">{error}</div> : null}

      <section className="training-grid">
        <aside className="panel training-nav">
          <div className="training-section-head">
            <h3><GraduationCap size={18} /> {t.modules}</h3>
            <span>{training?.modules.length ?? 0} {t.headingCount}</span>
          </div>
          {loading && !training ? (
            <p className="empty">{t.loading}</p>
          ) : (
            <div className="training-list">
              {training?.modules.map((module, index) => (
                <button
                  key={module.id}
                  className={index === selectedModuleIndex ? "training-list-item active" : "training-list-item"}
                  onClick={() => onSelectModule(index)}
                  type="button"
                >
                  <strong>{module.title}</strong>
                  <span>{module.summary}</span>
                </button>
              ))}
            </div>
          )}
        </aside>

        <article className="panel training-detail">
          <div className="training-section-head">
            <h3>{t.internMode}</h3>
            <span>{selectedModule?.id ?? t.waiting}</span>
          </div>
          {selectedModule ? (
            <>
              <p>{selectedModule.summary}</p>
              <div className="training-card-grid">
                <div className="training-card">
                  <strong>{t.teachingNotes}</strong>
                  <ul>
                    {selectedModule.lessonPoints.map((point) => <li key={point}>{point}</li>)}
                  </ul>
                </div>
                <div className="training-card">
                  <strong>{t.exercises}</strong>
                  <ul>
                    {selectedModule.exercises.map((exercise) => <li key={exercise}>{exercise}</li>)}
                  </ul>
                </div>
              </div>
              <div className="training-checklist">
                <strong>{t.checklist}</strong>
                {training?.checklist.map((item) => (
                  <div key={item.label} className={item.required ? "training-check required" : "training-check"}>
                    <span>{item.label}</span>
                    <small>{item.detail}</small>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <p className="empty">{t.selectModule}</p>
          )}
        </article>

        <aside className="panel training-studio">
          <div className="training-section-head">
            <h3><Sparkles size={18} /> {t.promptStudio}</h3>
            <span>{t.copyFocused}</span>
          </div>
          <div className="training-prompt-list">
            {training?.prompts.map((prompt, index) => (
              <button
                key={prompt.label}
                className={index === selectedPromptIndex ? "training-prompt active" : "training-prompt"}
                onClick={() => onSelectPrompt(index)}
                type="button"
              >
                <strong>{prompt.label}</strong>
                <span>{prompt.expectedOutput}</span>
              </button>
            ))}
          </div>
          <textarea
            className="training-draft"
            rows={12}
            value={draft}
            onChange={(event) => onChangeDraft(event.target.value)}
            placeholder={t.draftPlaceholder}
          />
          <div className="training-actions">
            <button className="secondary-button" onClick={() => void copyDraft()} type="button">
              <Copy size={17} />
              {t.copy}
            </button>
            <button
              type="button"
              onClick={() => void applyPrompt(selectedPrompt?.prompt ?? draft)}
              disabled={!selectedPrompt?.prompt && !draft.trim()}
            >
              {t.applySelection}
            </button>
          </div>
          <div className="training-card">
            <strong>{t.commonMistakes}</strong>
            <ul>
              {(training?.commonMistakes ?? []).map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
        </aside>
      </section>
    </section>
  );
}
