import { Lightbulb, Search } from 'lucide-react';
import type { HomeResponse } from '../types';

interface LogTabProps {
  home: HomeResponse | null;
  loading: boolean;
  error: string | null;
}

const DOMAIN_LABELS: Record<string, string> = {
  THINKING: '🧠 思考のクセ',
  VALUES: '💎 大切にしていること',
  BEHAVIOR: '🏃 行動パターン',
  INTERPERSONAL: '🤝 人との関わり方',
  MOTIVATION_INTERESTS: '🔥 やる気・興味の源',
};

export default function LogTab({ home, loading, error }: LogTabProps): JSX.Element {
  if (loading && home === null) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center" role="status">
        <div className="text-center">
          <div className="w-9 h-9 border-4 border-accentSoft border-t-accent rounded-full animate-spin mx-auto" />
          <p className="mt-3 text-sm text-textSecondary">じぶんログを読み込んでいます</p>
        </div>
      </div>
    );
  }

  if (home === null) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="w-full bg-surface border border-borderSubtle rounded-2xl p-6 text-center">
          <p className="font-bold text-textPrimary">じぶんログを表示できませんでした</p>
          <p className="mt-2 text-sm leading-relaxed text-textSecondary" role="alert">
            {error ?? 'しばらくしてから、もう一度お試しください。'}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <header>
        <p className="text-xs font-bold text-accent">YOUR LOG</p>
        <h1 className="mt-1 text-2xl font-black text-textPrimary">じぶんログ</h1>
        <p className="mt-2 text-sm leading-relaxed text-textSecondary">これまでのきろくから見えてきた、あなたらしさです。</p>
      </header>

      {error !== null && (
        <div className="bg-surface border border-borderStrong rounded-2xl p-3 text-sm text-textSecondary" role="alert">
          最新の情報に更新できませんでした。{error}
        </div>
      )}

      <section aria-labelledby="all-learnings-heading">
        <div className="flex items-center gap-2 mb-3 px-1">
          <Lightbulb size={18} className="text-accent" aria-hidden="true" />
          <h2 id="all-learnings-heading" className="text-lg font-black text-textPrimary">見えてきた傾向</h2>
          <span className="ml-auto bg-badgeBg text-badgeText text-[10px] font-bold px-2 py-1 rounded-badge">
            {home.current_learnings.length}件
          </span>
        </div>

        {home.current_learnings.length > 0 ? (
          <div className="space-y-3">
            {home.current_learnings.map((learning) => (
              <article key={learning.insight_id} className="bg-surface border border-borderSubtle rounded-insight p-4 shadow-sm">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-[11px] font-bold text-accent">{DOMAIN_LABELS[learning.domain] ?? learning.domain}</p>
                    <h3 className="mt-1 text-sm font-black text-textPrimary">{learning.title}</h3>
                  </div>
                  <span className="shrink-0 bg-badgeBg text-badgeText text-[10px] font-bold px-2 py-1 rounded-badge">
                    {learning.confidence_level}
                  </span>
                </div>
                <p className="mt-3 text-sm leading-relaxed text-textSecondary">{learning.statement}</p>
              </article>
            ))}
          </div>
        ) : (
          <div className="bg-surface border border-borderSubtle rounded-2xl p-5 text-center">
            <p className="text-sm font-bold text-textPrimary">まだ傾向はありません</p>
            <p className="mt-1 text-xs leading-relaxed text-textSecondary">きろくを重ねると、ここに気づきがたまっていきます。</p>
          </div>
        )}
      </section>

      <section aria-labelledby="evidence-gaps-heading">
        <div className="flex items-center gap-2 mb-3 px-1">
          <Search size={18} className="text-accent" aria-hidden="true" />
          <h2 id="evidence-gaps-heading" className="text-lg font-black text-textPrimary">もう少し知りたいこと</h2>
        </div>

        {home.evidence_gaps.length > 0 ? (
          <div className="space-y-3">
            {home.evidence_gaps.map((gap, index) => (
              <article key={`${gap.domain}-${index}`} className="bg-surfaceSecondary rounded-2xl p-4">
                <p className="text-xs font-bold text-textPrimary">{DOMAIN_LABELS[gap.domain] ?? gap.domain}</p>
                <p className="mt-2 text-sm leading-relaxed text-textSecondary">{gap.message}</p>
              </article>
            ))}
          </div>
        ) : (
          <div className="bg-accentSoft rounded-2xl p-4">
            <p className="text-sm font-bold text-textPrimary">いろいろな面が見えてきています</p>
            <p className="mt-1 text-xs leading-relaxed text-textSecondary">引き続き、日々の出来事を自由にきろくしてみてください。</p>
          </div>
        )}
      </section>
    </div>
  );
}
