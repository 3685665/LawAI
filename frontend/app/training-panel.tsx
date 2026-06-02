"use client";

import { Copy, GraduationCap, RefreshCw, Sparkles } from "lucide-react";
import { useMemo } from "react";

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
            <h2>{training?.title ?? "Dilekce egitimi"}</h2>
            <p>{training?.subtitle ?? "Egitim verisi yukleniyor..."}</p>
          </div>
          <button className="secondary-button" onClick={onReload} type="button">
            <RefreshCw size={17} />
            Yenile
          </button>
        </div>
        <p className="training-summary">{training?.summary ?? "Bu alan, dilekce yazim akisini stajyer mantigiyla parcalara ayirir."}</p>
        <div className="training-pills">
          {(training?.audience ?? ["Dosya ogrenme", "Dilekce kurma", "Son kontrol"]).map((item) => (
            <span key={item} className="training-pill">{item}</span>
          ))}
        </div>
        <div className="training-meta">
          <div>
            <span>Modul</span>
            <strong>{training?.modules.length ?? 0}</strong>
          </div>
          <div>
            <span>Prompt</span>
            <strong>{training?.prompts.length ?? 0}</strong>
          </div>
          <div>
            <span>Kontrol noktasi</span>
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
            <h3><GraduationCap size={18} /> Moduller</h3>
            <span>{training?.modules.length ?? 0} baslik</span>
          </div>
          {loading && !training ? (
            <p className="empty">Egitim verisi yukleniyor...</p>
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
            <h3>Stajyer modu</h3>
            <span>{selectedModule?.id ?? "bekleniyor"}</span>
          </div>
          {selectedModule ? (
            <>
              <p>{selectedModule.summary}</p>
              <div className="training-card-grid">
                <div className="training-card">
                  <strong>Ogretim notlari</strong>
                  <ul>
                    {selectedModule.lessonPoints.map((point) => <li key={point}>{point}</li>)}
                  </ul>
                </div>
                <div className="training-card">
                  <strong>Alistirmalar</strong>
                  <ul>
                    {selectedModule.exercises.map((exercise) => <li key={exercise}>{exercise}</li>)}
                  </ul>
                </div>
              </div>
              <div className="training-checklist">
                <strong>Kontrol listesi</strong>
                {training?.checklist.map((item) => (
                  <div key={item.label} className={item.required ? "training-check required" : "training-check"}>
                    <span>{item.label}</span>
                    <small>{item.detail}</small>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <p className="empty">Modul secin.</p>
          )}
        </article>

        <aside className="panel training-studio">
          <div className="training-section-head">
            <h3><Sparkles size={18} /> Prompt studio</h3>
            <span>Copy odakli</span>
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
            placeholder="Burada secilen prompt uzerinden calisilacak metin yer alir."
          />
          <div className="training-actions">
            <button className="secondary-button" onClick={() => void copyDraft()} type="button">
              <Copy size={17} />
              Kopyala
            </button>
            <button
              type="button"
              onClick={() => void applyPrompt(selectedPrompt?.prompt ?? draft)}
              disabled={!selectedPrompt?.prompt && !draft.trim()}
            >
              Secimi uygula
            </button>
          </div>
          <div className="training-card">
            <strong>Sik yapilan hatalar</strong>
            <ul>
              {(training?.commonMistakes ?? []).map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
        </aside>
      </section>
    </section>
  );
}
