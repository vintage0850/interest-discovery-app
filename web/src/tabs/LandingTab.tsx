import { Sparkles, CalendarClock, NotebookPen } from 'lucide-react';

interface LandingTabProps {
  onStart: () => void;
}

const FEATURES = [
  {
    icon: Sparkles,
    title: '5分でできる行動実験',
    description: '短いお題に答えるだけで、じぶんの興味・関心のヒントが見えてきます。',
  },
  {
    icon: NotebookPen,
    title: 'じぶんログ',
    description: 'きろくが積み重なるほど、思考のクセや大切にしていることが可視化されます。',
  },
  {
    icon: CalendarClock,
    title: 'Googleカレンダー連携',
    description: '行動実験のリマインダーをカレンダーに追加できます。',
  },
] as const;

export default function LandingTab({ onStart }: LandingTabProps): JSX.Element {
  return (
    <div className="space-y-6">
      <header className="pt-2">
        <p className="text-xs font-bold text-accent">SELF UNDERSTANDING</p>
        <h1 className="mt-1 text-2xl font-black text-textPrimary">Mikke（みっけ）</h1>
        <p className="mt-3 text-sm leading-relaxed text-textSecondary">
          Mikkeは、5分でできる短い行動実験を通じて高校生が自分の興味・関心を発見できるアプリです。タスク管理とGoogleカレンダー連携にも対応しています。
        </p>
      </header>

      <section className="space-y-3">
        {FEATURES.map((feature) => {
          const Icon = feature.icon;
          return (
            <article
              key={feature.title}
              className="bg-surface border border-borderSubtle rounded-2xl p-4 shadow-sm flex items-start gap-3"
            >
              <div className="shrink-0 bg-accentSoft text-accent rounded-full p-2">
                <Icon size={18} aria-hidden="true" />
              </div>
              <div>
                <h2 className="text-sm font-black text-textPrimary">{feature.title}</h2>
                <p className="mt-1 text-sm leading-relaxed text-textSecondary">{feature.description}</p>
              </div>
            </article>
          );
        })}
      </section>

      <button
        type="button"
        onClick={onStart}
        className="w-full bg-accent text-accentText rounded-button py-3.5 text-sm font-black shadow-sm active:scale-[0.99] transition"
      >
        はじめる
      </button>

      <p className="text-center text-xs text-textTertiary">
        <a
          href="https://mikke-discovery-backend.fly.dev/static/privacy-policy.html"
          target="_blank"
          rel="noreferrer"
          className="underline"
        >
          プライバシーポリシー
        </a>
      </p>
    </div>
  );
}
