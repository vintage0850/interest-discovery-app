import { ArrowRight, Lightbulb, Sparkles } from 'lucide-react';
import type { HomeResponse } from '../types';

interface HomeTabProps {
  home: HomeResponse | null;
  loading: boolean;
  error: string | null;
  onGoToLog: () => void;
}

const DOMAIN_LABELS: Record<string, string> = {
  THINKING: '🧠 思考のクセ',
  VALUES: '💎 大切にしていること',
  BEHAVIOR: '🏃 行動パターン',
  INTERPERSONAL: '🤝 人との関わり方',
  MOTIVATION_INTERESTS: '🔥 やる気・興味の源',
};

const BREADTH_LABELS = {
  LOW: 'これから',
  MEDIUM: '集まりつつある',
  HIGH: '十分にある',
} as const;

export default function HomeTab({ home, loading, error, onGoToLog }: HomeTabProps): JSX.Element {
  if (loading && home === null) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center" role="status">
        <div className="text-center">
          <div className="w-9 h-9 border-4 border-accentSoft border-t-accent rounded-full animate-spin mx-auto" />
          <p className="mt-3 text-sm text-textSecondary">じぶんの今を読み込んでいます</p>
        </div>
      </div>
    );
  }

  if (home === null) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="w-full bg-surface border border-borderSubtle rounded-2xl p-6 text-center">
          <p className="font-bold text-textPrimary">データを表示できませんでした</p>
          <p className="mt-2 text-sm leading-relaxed text-textSecondary" role="alert">
            {error ?? 'しばらくしてから、もう一度お試しください。'}
          </p>
        </div>
      </div>
    );
  }

  const learnings = home.current_learnings.slice(0, 3);

  return (
    <div className="space-y-5">
      <header>
        <p className="text-xs font-bold text-accent">SELF UNDERSTANDING</p>
        <h1 className="mt-1 text-2xl font-black text-textPrimary">ホーム</h1>
      </header>

      {error !== null && (
        <div className="bg-surface border border-borderStrong rounded-2xl p-3 text-sm text-textSecondary" role="alert">
          最新の情報に更新できませんでした。{error}
        </div>
      )}

      <section className="bg-accent text-accentText rounded-card p-5 shadow-sm">
        <div className="flex items-center gap-2 text-sm font-bold">
          <Sparkles size={17} aria-hidden="true" />
          <span>いまのあなた</span>
        </div>
        <p className="mt-3 text-sm leading-relaxed">{home.summary}</p>
      </section>

      <section aria-labelledby="domains-heading">
        <div className="mb-3 px-1">
          <p className="text-xs font-bold text-textTertiary">5つの視点</p>
          <h2 id="domains-heading" className="text-lg font-black text-textPrimary">じぶんの輪郭</h2>
        </div>
        <div className="space-y-3">
          {home.domains.map((domain) => (
            <article key={domain.domain} className="bg-surface border border-borderSubtle rounded-2xl p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <h3 className="text-sm font-black text-textPrimary">{DOMAIN_LABELS[domain.domain] ?? domain.domain}</h3>
                <div className="flex shrink-0 items-center gap-1.5">
                  {domain.has_new_change && (
                    <span className="bg-accentSoft text-accent text-[10px] font-bold px-2 py-1 rounded-badge">更新</span>
                  )}
                  <span className="bg-badgeBg text-badgeText text-[10px] font-bold px-2 py-1 rounded-badge">{domain.level}</span>
                </div>
              </div>
              <p className="mt-2 text-sm leading-relaxed text-textSecondary">{domain.summary}</p>
              <p className="mt-3 text-[11px] text-textTertiary">証拠の広がり：{BREADTH_LABELS[domain.evidence_breadth]}</p>
            </article>
          ))}
        </div>
      </section>

      <section aria-labelledby="learnings-heading">
        <button
          type="button"
          onClick={onGoToLog}
          className="w-full bg-accentSoft border border-accent/10 rounded-2xl p-4 text-left transition hover:border-accent/30 active:scale-[0.99]"
        >
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Lightbulb size={18} className="text-accent" aria-hidden="true" />
              <h2 id="learnings-heading" className="text-sm font-black text-textPrimary">見えてきた傾向</h2>
            </div>
            <ArrowRight size={18} className="shrink-0 text-accent" aria-hidden="true" />
          </div>
          <p className="mt-1 text-xs text-textSecondary">じぶんログですべて確認できます</p>
        </button>

        {learnings.length > 0 ? (
          <div className="mt-3 space-y-3">
            {learnings.map((learning) => (
              <article key={learning.insight_id} className="bg-surface border border-borderSubtle rounded-insight p-4">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="text-sm font-black text-textPrimary">{learning.title}</h3>
                  <span className="shrink-0 bg-badgeBg text-badgeText text-[10px] font-bold px-2 py-1 rounded-badge">
                    {learning.confidence_level}
                  </span>
                </div>
                <p className="mt-2 text-sm leading-relaxed text-textSecondary">{learning.statement}</p>
              </article>
            ))}
          </div>
        ) : (
          <p className="mt-3 bg-surface border border-borderSubtle rounded-2xl p-4 text-sm text-textSecondary">
            きろくが増えると、ここに気づきが表示されます。
          </p>
        )}
      </section>

      <section className="bg-surfaceSecondary rounded-2xl p-4" aria-labelledby="next-action-heading">
        <p className="text-xs font-bold text-textTertiary">NEXT ACTION</p>
        <h2 id="next-action-heading" className="mt-1 text-sm font-black text-textPrimary">次にできること</h2>
        <p className="mt-2 text-sm leading-relaxed text-textSecondary">{home.next_action.message}</p>
      </section>
    </div>
  );
}
