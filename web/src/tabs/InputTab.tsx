import { useState, type FormEvent } from 'react';
import { Check, Lightbulb, Send } from 'lucide-react';
import { submitFeedback, submitInput } from '../api';
import type { FeedbackType, InputResponse } from '../types';

interface InputTabProps {
  onSubmitted: () => void;
}

const FEEDBACK_OPTIONS: ReadonlyArray<{ value: FeedbackType; label: string }> = [
  { value: 'ACCURATE', label: 'その通り' },
  { value: 'PARTLY_ACCURATE', label: '一部そう' },
  { value: 'INACCURATE', label: 'ちがう' },
  { value: 'UNSURE', label: 'まだわからない' },
];

export default function InputTab({ onSubmitted }: InputTabProps): JSX.Element {
  const [text, setText] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<InputResponse | null>(null);
  const [selectedFeedback, setSelectedFeedback] = useState<FeedbackType | null>(null);
  const [feedbackSubmitting, setFeedbackSubmitting] = useState(false);
  const [feedbackSent, setFeedbackSent] = useState(false);
  const [feedbackError, setFeedbackError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedText = text.trim();
    if (trimmedText.length === 0 || submitting) return;

    setSubmitting(true);
    setSubmitError(null);
    setResult(null);
    setSelectedFeedback(null);
    setFeedbackSent(false);
    setFeedbackError(null);

    try {
      const response = await submitInput(trimmedText);
      setResult(response);
      setText('');
      onSubmitted();
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : 'きろくの送信に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const handleFeedback = async (feedback: FeedbackType) => {
    const insightId = result?.post_action.insight_id;
    if (insightId === undefined || feedbackSubmitting || feedbackSent) return;

    setSelectedFeedback(feedback);
    setFeedbackSubmitting(true);
    setFeedbackError(null);

    try {
      await submitFeedback(insightId, feedback);
      setFeedbackSent(true);
    } catch (error) {
      setFeedbackError(error instanceof Error ? error.message : 'フィードバックの送信に失敗しました');
    } finally {
      setFeedbackSubmitting(false);
    }
  };

  const insight = result?.post_action.type === 'INSIGHT' ? result.post_action : null;

  return (
    <div className="space-y-5">
      <header>
        <p className="text-xs font-bold text-accent">DAILY RECORD</p>
        <h1 className="mt-1 text-2xl font-black text-textPrimary">きろく</h1>
        <p className="mt-2 text-sm leading-relaxed text-textSecondary">
          今日あったことや感じたことを、まとまっていなくてもそのまま書いてください。
        </p>
      </header>

      <form onSubmit={handleSubmit} className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm">
        <label htmlFor="self-understanding-input" className="block text-sm font-black text-textPrimary mb-2">
          いま、何がありましたか？
        </label>
        <textarea
          id="self-understanding-input"
          value={text}
          onChange={(event) => setText(event.target.value)}
          disabled={submitting}
          rows={7}
          placeholder="例：グループで話すより、一人で考えてから意見を伝えたときのほうが落ち着いた。"
          className="w-full resize-none bg-background border border-borderSubtle rounded-2xl p-3 text-sm leading-relaxed text-textPrimary placeholder:text-textTertiary focus:outline-none focus:border-accent focus:ring-2 focus:ring-accentSoft disabled:opacity-60"
        />
        <div className="mt-3 flex items-center justify-between gap-3">
          <span className="text-[11px] text-textTertiary">自由な言葉で大丈夫です</span>
          <button
            type="submit"
            disabled={text.trim().length === 0 || submitting}
            className="inline-flex items-center justify-center gap-2 bg-accent hover:bg-accentDark disabled:opacity-40 disabled:cursor-not-allowed text-accentText text-sm font-bold px-5 py-3 rounded-button transition"
          >
            <Send size={16} aria-hidden="true" />
            {submitting ? '送信中…' : 'きろくする'}
          </button>
        </div>
      </form>

      {submitError !== null && (
        <div className="bg-surface border border-borderStrong rounded-2xl p-4" role="alert">
          <p className="text-sm font-bold text-textPrimary">送信できませんでした</p>
          <p className="mt-1 text-xs leading-relaxed text-textSecondary">{submitError}</p>
        </div>
      )}

      {result !== null && insight === null && (
        <div className="bg-accentSoft rounded-2xl p-4" role="status">
          <div className="flex items-center gap-2 text-accent font-bold text-sm">
            <Check size={17} aria-hidden="true" />
            <span>きろくしました</span>
          </div>
          <p className="mt-2 text-sm text-textSecondary">きろくが増えると、少しずつ傾向が見えてきます。</p>
        </div>
      )}

      {insight !== null && (
        <section className="bg-surface border border-accent/20 rounded-insight p-5 shadow-sm" aria-labelledby="new-insight-heading">
          <div className="flex items-center gap-2 text-accent">
            <Lightbulb size={19} aria-hidden="true" />
            <p className="text-xs font-black">新しい気づき</p>
          </div>
          <h2 id="new-insight-heading" className="mt-3 text-lg font-black text-textPrimary">
            {insight.title ?? 'あなたについて見えてきたこと'}
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-textSecondary">
            {insight.statement ?? 'きろくから新しい傾向が見つかりました。'}
          </p>
          {insight.confidence_level !== undefined && (
            <span className="inline-block mt-3 bg-badgeBg text-badgeText text-[10px] font-bold px-2 py-1 rounded-badge">
              確信度：{insight.confidence_level}
            </span>
          )}

          {insight.insight_id !== undefined && (
            <div className="mt-5 pt-4 border-t border-borderSubtle">
              <p className="text-sm font-bold text-textPrimary">この気づきは、どのくらい当てはまりますか？</p>
              <div className="grid grid-cols-2 gap-2 mt-3">
                {FEEDBACK_OPTIONS.map((option) => {
                  const isSelected = selectedFeedback === option.value;
                  return (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => handleFeedback(option.value)}
                      disabled={feedbackSubmitting || feedbackSent}
                      className={`rounded-selector border px-3 py-2.5 text-xs font-bold transition disabled:cursor-not-allowed ${
                        isSelected
                          ? 'bg-accent text-accentText border-accent'
                          : 'bg-background text-textSecondary border-borderSubtle hover:border-accent'
                      }`}
                    >
                      {option.label}
                    </button>
                  );
                })}
              </div>
              {feedbackSubmitting && <p className="mt-3 text-xs text-textTertiary" role="status">送信しています…</p>}
              {feedbackSent && (
                <p className="mt-3 flex items-center gap-1.5 text-xs font-bold text-accent" role="status">
                  <Check size={14} aria-hidden="true" />
                  教えてくれてありがとうございます
                </p>
              )}
              {feedbackError !== null && <p className="mt-3 text-xs text-textSecondary" role="alert">{feedbackError}</p>}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
