import { useState, useEffect } from 'react';
import { 
  INITIAL_EXPERIMENTS, 
  INITIAL_DOMAINS, 
  SIGNAL_THEMES,
  Experiment
} from './data';
import { 
  Home as HomeIcon, 
  Compass, 
  BarChart3, 
  Settings as SettingsIcon, 
  Play, 
  Sparkles, 
  ChevronDown, 
  ChevronUp, 
  ArrowRight,
  Flame,
  User,
  MessageCircle,
  Bell,
  Shield,
  FileText,
  ChevronRight,
  X,
  Check,
  Mail
} from 'lucide-react';

export default function App() {
  const [currentTab, setCurrentTab] = useState<'home' | 'explore' | 'log' | 'settings'>('home');
  const [completedCount, setCompletedCount] = useState(2);
  
  // 今日のメイン実験（デフォルトは1件目）
  const [featuredIndex, setFeaturedIndex] = useState(0);
  const [isAlternativesOpen, setIsAlternativesOpen] = useState(false);

  // 画面遷移
  const [screen, setScreen] = useState<'main' | 'detail' | 'running' | 'reflection' | 'result'>('main');
  const [activeExperiment, setActiveExperiment] = useState<Experiment | null>(null);

  // タイマー状態
  const [timerSeconds, setTimerSeconds] = useState(0);
  const [isTimerRunning, setIsTimerRunning] = useState(false);

  // 振り返り評価
  const [enjoyment, setEnjoyment] = useState<number | null>(null);
  const [curiosity, setCuriosity] = useState<number | null>(null);
  const [retryIntent, setRetryIntent] = useState<number | null>(null);

  // Exploreフィルター
  const [exploreFilter, setExploreFilter] = useState<'ALL' | 'UNEXPLORED' | 'EXPLORED' | 'TRIED' | 'DIVE_CANDIDATE'>('ALL');
  // じぶんログのアコーディオン
  const [isEvidenceOpen, setIsEvidenceOpen] = useState(false);

  // 設定用モーダル状態
  const [activeSettingsModal, setActiveSettingsModal] = useState<
    'none' | 'account' | 'line' | 'notification' | 'privacy' | 'terms' | 'policy'
  >('none');

  // 通知設定の各トグル
  const [notifyDailyReminder, setNotifyDailyReminder] = useState(true);
  const [notifyStreak, setNotifyStreak] = useState(true);
  const [notifyLine, setNotifyLine] = useState(false);
  const [notifyInApp, setNotifyInApp] = useState(true);

  // プライバシー設定トグル
  const [allowAnalytics, setAllowAnalytics] = useState(true);

  // タイマーのカウントアップ
  useEffect(() => {
    let interval: any = null;
    if (isTimerRunning) {
      interval = setInterval(() => {
        setTimerSeconds(s => s + 1);
      }, 1000);
    } else {
      clearInterval(interval);
    }
    return () => clearInterval(interval);
  }, [isTimerRunning]);

  const heroExperiment = INITIAL_EXPERIMENTS[featuredIndex];
  const heroTheme = SIGNAL_THEMES[heroExperiment.actionType];

  const handleStartExperiment = (exp: Experiment) => {
    setActiveExperiment(exp);
    setScreen('detail');
  };

  const handleBeginTimer = () => {
    setTimerSeconds(0);
    setIsTimerRunning(true);
    setScreen('running');
  };

  const handleFinishTimer = () => {
    setIsTimerRunning(false);
    setEnjoyment(null);
    setCuriosity(null);
    setRetryIntent(null);
    setScreen('reflection');
  };

  const handleSubmitReflection = () => {
    setCompletedCount(c => c + 1);
    setScreen('result');
  };

  const handleBackToMain = () => {
    setScreen('main');
    setActiveExperiment(null);
  };

  // -------------------------------------------------------------
  // 1. 実験詳細画面
  // -------------------------------------------------------------
  if (screen === 'detail' && activeExperiment) {
    const theme = SIGNAL_THEMES[activeExperiment.actionType];
    return (
      <div className="flex-1 flex flex-col h-full bg-background p-5 overflow-y-auto">
        <button onClick={handleBackToMain} className="text-textSecondary text-xs font-semibold self-start mb-4 py-1 flex items-center gap-1">
          ← もどる
        </button>
        
        <div className="flex gap-2 mb-3">
          <span className="bg-badgeBg text-badgeText text-xs font-bold px-2.5 py-1 rounded-badge">
            ⏱️ {activeExperiment.plannedMinutes}分
          </span>
          <span className={`${theme.badgeBg} text-xs font-bold px-2.5 py-1 rounded-badge flex items-center gap-1`}>
            <span>{theme.icon}</span>
            <span>{theme.label}</span>
          </span>
        </div>

        <h1 className="text-2xl font-black text-textPrimary leading-tight mb-2">
          {activeExperiment.title}
        </h1>
        <p className="text-xs text-textSecondary font-medium mb-5">
          {activeExperiment.subtitle}
        </p>

        {/* 手順ステップ */}
        <div className="bg-surface border border-borderSubtle rounded-2xl p-4 mb-4 shadow-sm">
          <div className="text-xs font-bold text-textPrimary mb-3 flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-accent"></span> やること（3ステップ）
          </div>
          <div className="space-y-2.5">
            {activeExperiment.steps.map((step, idx) => (
              <div key={idx} className="flex items-start gap-2.5 text-xs text-textSecondary leading-relaxed">
                <span className="w-5 h-5 rounded-full bg-surfaceSecondary text-textPrimary font-mono font-bold flex items-center justify-center shrink-0 text-[11px]">
                  {idx + 1}
                </span>
                <span className="pt-0.5">{step}</span>
              </div>
            ))}
          </div>
        </div>

        {/* ねらい */}
        <div className="bg-surfaceSecondary/70 border border-borderSubtle rounded-2xl p-4 mb-6">
          <div className="text-[11px] font-bold text-textTertiary mb-1">💡 見てみたいこと</div>
          <p className="text-xs text-textPrimary leading-relaxed">
            {activeExperiment.hypothesisShort}
          </p>
        </div>

        <button 
          onClick={handleBeginTimer}
          className="w-full bg-accent hover:bg-accentDark active:scale-[0.98] transition text-accentText font-bold text-base py-4 rounded-2xl shadow-lg shadow-accent/20 flex items-center justify-center gap-2 mt-auto"
        >
          <Play size={18} fill="currentColor" />
          <span>実験をスタート（{activeExperiment.plannedMinutes}分）</span>
        </button>
      </div>
    );
  }

  // -------------------------------------------------------------
  // 2. タイマー実行画面
  // -------------------------------------------------------------
  if (screen === 'running' && activeExperiment) {
    const theme = SIGNAL_THEMES[activeExperiment.actionType];
    const min = Math.floor(timerSeconds / 60);
    const sec = timerSeconds % 60;
    const timeStr = `${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;

    return (
      <div className="flex-1 flex flex-col h-full bg-background p-6 justify-between items-center text-center">
        <div>
          <div className="inline-flex gap-2 mb-3 mt-2">
            <span className="bg-badgeBg text-badgeText text-xs font-bold px-2.5 py-1 rounded-badge">
              ⏱️ 目安 {activeExperiment.plannedMinutes}分
            </span>
            <span className={`${theme.badgeBg} text-xs font-bold px-2.5 py-1 rounded-badge flex items-center gap-1`}>
              <span>{theme.icon}</span>
              <span>{theme.shortLabel}</span>
            </span>
          </div>
          <h2 className="text-xl font-black text-textPrimary mb-1">{activeExperiment.title}</h2>
          <p className="text-xs text-textSecondary">{activeExperiment.subtitle}</p>
        </div>

        {/* タイマービジュアル */}
        <div className="bg-surface border-2 border-accent/20 rounded-3xl p-8 shadow-md flex flex-col items-center w-full max-w-[280px]">
          <span className="text-[11px] font-bold tracking-widest text-textTertiary uppercase mb-2">経過時間</span>
          <span className="text-5xl font-mono font-black text-accent tracking-tight mb-3">{timeStr}</span>
          <span className="text-[11px] text-textSecondary leading-snug">
            やってみて「心地いい・楽しい」と<br />感じるか意識してみよう
          </span>
        </div>

        <div className="w-full space-y-2">
          <button 
            onClick={handleFinishTimer}
            className="w-full bg-accent hover:bg-accentDark active:scale-[0.98] transition text-accentText font-bold text-base py-4 rounded-2xl shadow-lg shadow-accent/20"
          >
            完了して振り返る ✨
          </button>
          <button 
            onClick={handleBackToMain}
            className="text-textTertiary text-xs font-medium py-2"
          >
            中断してホームにもどる
          </button>
        </div>
      </div>
    );
  }

  // -------------------------------------------------------------
  // 3. 振り返り画面（直感3問）
  // -------------------------------------------------------------
  if (screen === 'reflection') {
    const isValid = enjoyment !== null && curiosity !== null && retryIntent !== null;

    return (
      <div className="flex-1 flex flex-col h-full bg-background p-5 overflow-y-auto">
        <h1 className="text-2xl font-black text-textPrimary mb-1">やってみてどうだった？</h1>
        <p className="text-xs text-textSecondary mb-6">今の直感を 1〜5 でタップしよう！</p>

        <div className="space-y-6 mb-6">
          <div className="bg-surface border border-borderSubtle rounded-2xl p-4 shadow-sm">
            <p className="text-xs font-bold text-textPrimary mb-3">1. やっていて楽しかった？</p>
            <div className="flex justify-between gap-1.5">
              {[1, 2, 3, 4, 5].map(score => (
                <button
                  key={score}
                  onClick={() => setEnjoyment(score)}
                  className={`flex-1 h-12 rounded-xl font-bold text-base transition ${
                    enjoyment === score 
                      ? 'bg-accent text-accentText shadow-md scale-105' 
                      : 'bg-surfaceSecondary text-textPrimary hover:bg-borderSubtle'
                  }`}
                >
                  {score}
                </button>
              ))}
            </div>
            <div className="flex justify-between text-[10px] text-textTertiary mt-2 px-1">
              <span>あまり思わない</span>
              <span>すごく楽しかった！</span>
            </div>
          </div>

          <div className="bg-surface border border-borderSubtle rounded-2xl p-4 shadow-sm">
            <p className="text-xs font-bold text-textPrimary mb-3">2. もっと知りたくなった？</p>
            <div className="flex justify-between gap-1.5">
              {[1, 2, 3, 4, 5].map(score => (
                <button
                  key={score}
                  onClick={() => setCuriosity(score)}
                  className={`flex-1 h-12 rounded-xl font-bold text-base transition ${
                    curiosity === score 
                      ? 'bg-accent text-accentText shadow-md scale-105' 
                      : 'bg-surfaceSecondary text-textPrimary hover:bg-borderSubtle'
                  }`}
                >
                  {score}
                </button>
              ))}
            </div>
            <div className="flex justify-between text-[10px] text-textTertiary mt-2 px-1">
              <span>あまり思わない</span>
              <span>もっと知りたい！</span>
            </div>
          </div>

          <div className="bg-surface border border-borderSubtle rounded-2xl p-4 shadow-sm">
            <p className="text-xs font-bold text-textPrimary mb-3">3. 似たようなことをまたやってみたい？</p>
            <div className="flex justify-between gap-1.5">
              {[1, 2, 3, 4, 5].map(score => (
                <button
                  key={score}
                  onClick={() => setRetryIntent(score)}
                  className={`flex-1 h-12 rounded-xl font-bold text-base transition ${
                    retryIntent === score 
                      ? 'bg-accent text-accentText shadow-md scale-105' 
                      : 'bg-surfaceSecondary text-textPrimary hover:bg-borderSubtle'
                  }`}
                >
                  {score}
                </button>
              ))}
            </div>
            <div className="flex justify-between text-[10px] text-textTertiary mt-2 px-1">
              <span>あまり思わない</span>
              <span>またやりたい！</span>
            </div>
          </div>
        </div>

        <button 
          onClick={handleSubmitReflection}
          disabled={!isValid}
          className={`w-full py-4 rounded-2xl font-bold text-base shadow-lg transition mt-auto ${
            isValid 
              ? 'bg-accent hover:bg-accentDark text-accentText active:scale-[0.98] shadow-accent/20' 
              : 'bg-borderStrong text-textTertiary cursor-not-allowed'
          }`}
        >
          記録を保存する 🎉
        </button>
      </div>
    );
  }

  // -------------------------------------------------------------
  // 4. 発見結果画面
  // -------------------------------------------------------------
  if (screen === 'result') {
    return (
      <div className="flex-1 flex flex-col h-full bg-background p-6 overflow-y-auto justify-between">
        <div>
          <div className="inline-flex items-center gap-1.5 bg-accentSoft text-accent font-bold text-xs px-3 py-1.5 rounded-full mb-3">
            <Sparkles size={14} /> 行動シグナルを記録しました！
          </div>
          <h1 className="text-2xl font-black text-textPrimary mb-1">新しい発見 🌱</h1>
          <p className="text-xs text-textSecondary mb-6">直近の行動から見えてきたあなたの関心です。</p>

          <div className="bg-surface border border-borderSubtle rounded-2xl p-5 mb-4 shadow-sm">
            <div className="text-[11px] font-bold text-accent mb-1 flex items-center gap-1">
              <span>💡</span> 見えてきたサイン
            </div>
            <div className="text-sm font-bold text-textPrimary leading-relaxed mb-2">
              「情報や画面の構造を観察する」行動に自然と集中できる傾向があります。
            </div>
            <p className="text-xs text-textSecondary leading-relaxed">
              普段使っているアプリやサービスの工夫に気づく力（デザイン・UI適性）が高いサインです。
            </p>
          </div>

          <div className="bg-surfaceSecondary/80 border border-borderSubtle rounded-2xl p-4 mb-4">
            <div className="text-[11px] font-bold text-textTertiary mb-1">🌱 今週の達成状況</div>
            <div className="text-xs font-semibold text-textPrimary">
              今週 <span className="text-accent font-black">{completedCount}回</span> の実験を完了しました！
            </div>
          </div>
        </div>

        <button 
          onClick={handleBackToMain}
          className="w-full bg-accent hover:bg-accentDark text-accentText font-bold py-4 rounded-2xl shadow-lg shadow-accent/20 active:scale-[0.98] transition text-base"
        >
          ホームにもどる
        </button>
      </div>
    );
  }

  // -------------------------------------------------------------
  // 5. メイン画面（4タブ構成）
  // -------------------------------------------------------------
  return (
    <div className="flex-1 flex flex-col h-full justify-between relative">
      {/* 画面スクロールエリア */}
      <div className="flex-1 overflow-y-auto p-4 pb-20">
        
        {/* ========================================================= */}
        {/* TAB 1: ホーム（今日やること） */}
        {/* ========================================================= */}
        {currentTab === 'home' && (
          <div>
            {/* 上部ヘッダー */}
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <img src="./icon.png" alt="Mikke" className="w-7 h-7 rounded-xl object-cover shadow-xs border border-borderSubtle" />
                <span className="font-black text-lg text-textPrimary tracking-tight">Mikke</span>
              </div>
              <span className="text-[11px] font-semibold text-textTertiary bg-surface px-2.5 py-1 rounded-full border border-borderSubtle">
                高校生向け 5分実験
              </span>
            </div>

            {/* 達成感プログレスバー（最上部） */}
            <div className="bg-surface border border-borderSubtle rounded-2xl p-3.5 mb-4 shadow-sm">
              <div className="flex items-center justify-between mb-1.5">
                <div className="flex items-center gap-1.5">
                  <Flame size={15} className="text-amber-500 fill-amber-500" />
                  <span className="text-xs font-bold text-textPrimary">
                    今週の実験: <span className="text-accent font-black">{Math.min(completedCount, 3)} / 3 完了</span>
                  </span>
                </div>
                <span className="text-[11px] text-textTertiary font-medium">
                  {completedCount >= 3 ? '🎉 今週達成！' : 'あと1回で目標達成！'}
                </span>
              </div>
              <div className="w-full bg-surfaceSecondary h-2 rounded-full overflow-hidden">
                <div 
                  className="bg-accent h-full rounded-full transition-all duration-500"
                  style={{ width: `${Math.min((completedCount / 3) * 100, 100)}%` }}
                ></div>
              </div>
            </div>

            {/* 🔥 主役カード（Hero Card）: 今日これやってみない？ */}
            <div className="mb-4">
              <div className="flex items-center justify-between mb-2">
                <h2 className="text-sm font-black text-textPrimary flex items-center gap-1">
                  <span>✨</span> 今日これやってみない？
                </h2>
                <span className="text-[11px] text-accent font-bold">イチオシ！</span>
              </div>

              <div className={`rounded-3xl p-5 border shadow-md transition-all ${heroTheme.cardBg}`}>
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <span className="bg-surface/90 text-textPrimary text-xs font-bold px-2.5 py-1 rounded-badge shadow-xs">
                      ⏱️ {heroExperiment.plannedMinutes}分
                    </span>
                    <span className={`${heroTheme.badgeBg} text-xs font-bold px-2.5 py-1 rounded-badge flex items-center gap-1 shadow-xs`}>
                      <span>{heroTheme.icon}</span>
                      <span>{heroTheme.label}</span>
                    </span>
                  </div>
                  <span className="text-2xl">{heroTheme.icon}</span>
                </div>

                <h3 className="text-lg font-black text-textPrimary leading-snug mb-1">
                  {heroExperiment.title}
                </h3>
                <p className="text-xs text-textSecondary font-medium mb-4 leading-relaxed">
                  {heroExperiment.subtitle}
                </p>

                {/* 短いステップ表示 */}
                <div className="bg-surface/80 rounded-xl p-3 mb-4 space-y-1.5 border border-white/60">
                  {heroExperiment.steps.map((step, idx) => (
                    <div key={idx} className="flex items-center gap-2 text-xs text-textSecondary">
                      <span className="w-4 h-4 rounded-full bg-accent/10 text-accent font-bold text-[10px] flex items-center justify-center shrink-0">
                        {idx + 1}
                      </span>
                      <span className="truncate">{step}</span>
                    </div>
                  ))}
                </div>

                {/* 特大プライマリボタン */}
                <button 
                  onClick={() => handleStartExperiment(heroExperiment)}
                  className="w-full bg-accent hover:bg-accentDark active:scale-[0.98] transition text-accentText font-black text-sm py-3.5 rounded-xl shadow-md shadow-accent/20 flex items-center justify-center gap-1.5"
                >
                  <Play size={16} fill="currentColor" />
                  <span>今すぐやってみる（{heroExperiment.plannedMinutes}分）</span>
                </button>
              </div>
            </div>

            {/* ほかの実験を見る（アコーディオンで展開） */}
            <div className="mb-5">
              <button 
                onClick={() => setIsAlternativesOpen(!isAlternativesOpen)}
                className="w-full bg-surface border border-borderSubtle hover:border-accent/40 rounded-2xl p-3.5 flex items-center justify-between text-xs font-bold text-textSecondary shadow-xs transition"
              >
                <span>ほかの実験も見てみる（あと2件）</span>
                {isAlternativesOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
              </button>

              {isAlternativesOpen && (
                <div className="mt-2.5 space-y-2.5">
                  {INITIAL_EXPERIMENTS.map((exp, index) => {
                    if (index === featuredIndex) return null;
                    const altTheme = SIGNAL_THEMES[exp.actionType];
                    return (
                      <div 
                        key={exp.id}
                        className={`rounded-2xl p-4 border transition ${altTheme.cardBg} shadow-xs flex flex-col justify-between`}
                      >
                        <div className="flex items-center justify-between mb-2">
                          <div className="flex items-center gap-2">
                            <span className="bg-surface/90 text-textPrimary text-[11px] font-bold px-2 py-0.5 rounded-badge">
                              ⏱️ {exp.plannedMinutes}分
                            </span>
                            <span className={`${altTheme.badgeBg} text-[11px] font-bold px-2 py-0.5 rounded-badge flex items-center gap-1`}>
                              <span>{altTheme.icon}</span>
                              <span>{altTheme.shortLabel}</span>
                            </span>
                          </div>
                          <button 
                            onClick={() => setFeaturedIndex(index)}
                            className="text-[11px] font-bold text-textTertiary hover:text-accent"
                          >
                            メインにする
                          </button>
                        </div>
                        <h4 className="text-sm font-bold text-textPrimary mb-1">{exp.title}</h4>
                        <p className="text-xs text-textSecondary mb-3">{exp.subtitle}</p>
                        
                        <button 
                          onClick={() => handleStartExperiment(exp)}
                          className="self-end bg-surface hover:bg-white text-accent font-bold text-xs px-4 py-2 rounded-xl border border-accent/30 shadow-xs flex items-center gap-1"
                        >
                          <span>これにする</span>
                          <ArrowRight size={13} />
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 見えてきた傾向ミニバナー */}
            <div 
              onClick={() => setCurrentTab('log')}
              className="bg-surface border border-borderSubtle hover:border-accent rounded-2xl p-3.5 cursor-pointer transition flex items-center justify-between shadow-xs"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-full bg-accentSoft text-accent flex items-center justify-center shrink-0">
                  <Sparkles size={16} />
                </div>
                <div>
                  <div className="text-[11px] font-bold text-accent">💡 見えてきた傾向</div>
                  <div className="text-xs font-bold text-textPrimary">「UI・画面の配置」への関心が高まっています</div>
                </div>
              </div>
              <span className="text-xs text-accent font-bold">じぶんログ →</span>
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* TAB 2: 分野をみる（探索） */}
        {/* ========================================================= */}
        {currentTab === 'explore' && (
          <div>
            <div className="mb-3">
              <h1 className="text-xl font-black text-textPrimary">分野をみる</h1>
              <p className="text-xs text-textSecondary mt-0.5">まだ試していない領域に小さく触れてみよう</p>
            </div>

            {/* フィルターチップ */}
            <div className="flex gap-1.5 overflow-x-auto pb-2 mb-3">
              {[
                { id: 'ALL', label: 'すべて' },
                { id: 'DIVE_CANDIDATE', label: '気になる 🔥' },
                { id: 'TRIED', label: 'Try済み' },
                { id: 'UNEXPLORED', label: '未探索' },
              ].map(f => (
                <button
                  key={f.id}
                  onClick={() => setExploreFilter(f.id as any)}
                  className={`px-3 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition ${
                    exploreFilter === f.id 
                      ? 'bg-accent text-accentText shadow-xs' 
                      : 'bg-surface border border-borderSubtle text-textSecondary'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>

            {/* 分野カード一覧 */}
            <div className="space-y-2.5">
              {INITIAL_DOMAINS
                .filter(d => exploreFilter === 'ALL' || d.status === exploreFilter)
                .map(domain => (
                  <div key={domain.id} className="bg-surface border border-borderSubtle rounded-2xl p-4 shadow-sm hover:border-accent/40 transition">
                    <div className="flex items-center justify-between mb-1.5">
                      <div className="flex items-center gap-2">
                        <span className="text-2xl">{domain.icon}</span>
                        <div>
                          <h3 className="text-sm font-bold text-textPrimary">{domain.name}</h3>
                          <p className="text-[11px] text-textSecondary">{domain.tagline}</p>
                        </div>
                      </div>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 ${
                        domain.status === 'DIVE_CANDIDATE' ? 'bg-amber-100 text-amber-800' :
                        domain.status === 'TRIED' ? 'bg-accentSoft text-accent' :
                        'bg-gray-100 text-gray-600'
                      }`}>
                        {domain.status === 'DIVE_CANDIDATE' ? '気になる 🔥' :
                         domain.status === 'TRIED' ? 'Try済み' : '未探索'}
                      </span>
                    </div>

                    <div className="flex items-center justify-between text-[11px] text-textTertiary pt-2 mt-2 border-t border-borderSubtle">
                      <span>実験 {domain.completedCount}/{domain.totalCount} 件</span>
                      <span className="text-accent font-semibold">{domain.signalTag}</span>
                    </div>
                  </div>
                ))}
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* TAB 3: じぶんログ（発見 ＋ レポートを1つに統合） */}
        {/* ========================================================= */}
        {currentTab === 'log' && (
          <div>
            <div className="mb-4">
              <h1 className="text-xl font-black text-textPrimary">じぶんログ</h1>
              <p className="text-xs text-textSecondary mt-0.5">行動データから見えてきたあなたの傾向と変化</p>
            </div>

            {/* サマリー数字 */}
            <div className="grid grid-cols-2 gap-2.5 mb-4">
              <div className="bg-surface border border-borderSubtle rounded-2xl p-3.5 text-center shadow-xs">
                <div className="text-2xl font-black text-accent">{completedCount + 2}</div>
                <div className="text-[11px] font-medium text-textSecondary mt-0.5">完了した実験</div>
              </div>
              <div className="bg-surface border border-borderSubtle rounded-2xl p-3.5 text-center shadow-xs">
                <div className="text-2xl font-black text-accent">28分</div>
                <div className="text-[11px] font-medium text-textSecondary mt-0.5">合計実験時間</div>
              </div>
            </div>

            {/* 見えてきたこと */}
            <div className="bg-surface border border-borderSubtle rounded-2xl p-4 shadow-sm mb-4">
              <div className="text-xs font-bold text-accent mb-1 flex items-center gap-1">
                <span>💡</span> 今わかってきたこと
              </div>
              <div className="text-sm font-bold text-textPrimary mb-1">UI・情報設計への興味</div>
              <p className="text-xs text-textSecondary leading-relaxed mb-3">
                画面のボタン配置やデザインの理由を考える実験で、もっとも高い楽しさ・没頭度を記録しています。
              </p>

              {/* 根拠アコーディオン */}
              <div className="bg-surfaceSecondary rounded-xl overflow-hidden">
                <button 
                  onClick={() => setIsEvidenceOpen(!isEvidenceOpen)}
                  className="w-full p-2.5 flex items-center justify-between text-left text-[11px] font-bold text-textSecondary"
                >
                  <span>❓ なぜそう表示された？（判定の根拠）</span>
                  {isEvidenceOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                </button>
                {isEvidenceOpen && (
                  <div className="px-3 pb-3 text-[11px] text-textSecondary border-t border-borderSubtle/60 pt-2 leading-relaxed">
                    直近3回の観察実験で「楽しかった」「またやりたい」のスコアが平均4.5点以上であり、予定時間を超えて観察した行動ログに基づいています。
                  </div>
                )}
              </div>
            </div>

            {/* シグナル分布 */}
            <div className="bg-surface border border-borderSubtle rounded-2xl p-4 shadow-sm mb-4">
              <div className="text-xs font-bold text-textPrimary mb-3">伸びているシグナル</div>
              <div className="space-y-2.5">
                {[
                  { name: '🔍 分析・観察', count: 4, width: '80%', color: 'bg-blue-500' },
                  { name: '⚖️ 比べる', count: 3, width: '60%', color: 'bg-purple-500' },
                  { name: '🎨 つくる', count: 2, width: '40%', color: 'bg-amber-500' },
                  { name: '🗂️ 整理する', count: 1, width: '20%', color: 'bg-cyan-500' },
                ].map(s => (
                  <div key={s.name}>
                    <div className="flex justify-between text-xs text-textPrimary mb-1">
                      <span className="font-medium">{s.name}</span>
                      <span className="font-mono text-textTertiary text-[11px]">{s.count}回</span>
                    </div>
                    <div className="w-full bg-surfaceSecondary h-2 rounded-full overflow-hidden">
                      <div className={`${s.color} h-full rounded-full`} style={{ width: s.width }}></div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 変化 */}
            <div className="bg-surfaceSecondary border border-borderSubtle rounded-2xl p-3.5">
              <div className="text-xs font-bold text-textSecondary mb-1 flex items-center gap-1">
                <span>📈</span> 過去の自分との変化
              </div>
              <p className="text-xs text-textPrimary leading-relaxed">
                先月は「つくる」中心でしたが、今月は「仕組みを見る」「比べる」ことへの関心が急上昇しています。
              </p>
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* TAB 4: 設定（要件通りの構成） */}
        {/* ========================================================= */}
        {currentTab === 'settings' && (
          <div>
            <div className="mb-4">
              <h1 className="text-xl font-black text-textPrimary">設定</h1>
            </div>

            <div className="space-y-5">
              {/* 1. アカウント セクション */}
              <div>
                <div className="text-xs font-bold text-textTertiary px-1 mb-2">アカウント</div>
                <div className="bg-surface border border-borderSubtle rounded-2xl overflow-hidden shadow-xs">
                  <div 
                    onClick={() => setActiveSettingsModal('account')}
                    className="p-4 flex items-center justify-between cursor-pointer hover:bg-surfaceSecondary/50 active:bg-surfaceSecondary transition"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-surfaceSecondary text-textSecondary flex items-center justify-center shrink-0">
                        <User size={16} />
                      </div>
                      <div>
                        <div className="text-sm font-bold text-textPrimary">アカウントを作成</div>
                        <div className="text-xs text-textTertiary mt-0.5">未ログイン</div>
                      </div>
                    </div>
                    <ChevronRight size={18} className="text-textTertiary shrink-0" />
                  </div>
                </div>
              </div>

              {/* 2. 連携 セクション */}
              <div>
                <div className="text-xs font-bold text-textTertiary px-1 mb-2">連携</div>
                <div className="bg-surface border border-borderSubtle rounded-2xl overflow-hidden shadow-xs">
                  <div 
                    onClick={() => setActiveSettingsModal('line')}
                    className="p-4 flex items-center justify-between cursor-pointer hover:bg-surfaceSecondary/50 active:bg-surfaceSecondary transition"
                  >
                    <div className="flex items-start gap-3">
                      <div className="w-8 h-8 rounded-full bg-[#06C755]/10 text-[#06C755] flex items-center justify-center shrink-0 mt-0.5">
                        <MessageCircle size={16} />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-bold text-textPrimary">LINE連携</span>
                          <span className="text-[10px] bg-badgeBg text-textTertiary px-2 py-0.5 rounded-full font-medium">未連携</span>
                        </div>
                        <div className="text-xs text-textSecondary leading-relaxed mt-1">
                          公式LINEから今日の実験やリマインドを受け取れます
                        </div>
                      </div>
                    </div>
                    <ChevronRight size={18} className="text-textTertiary shrink-0 ml-2" />
                  </div>
                </div>
              </div>

              {/* 3. 通知 セクション */}
              <div>
                <div className="text-xs font-bold text-textTertiary px-1 mb-2">通知</div>
                <div className="bg-surface border border-borderSubtle rounded-2xl overflow-hidden shadow-xs">
                  <div 
                    onClick={() => setActiveSettingsModal('notification')}
                    className="p-4 flex items-center justify-between cursor-pointer hover:bg-surfaceSecondary/50 active:bg-surfaceSecondary transition"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-surfaceSecondary text-textSecondary flex items-center justify-center shrink-0">
                        <Bell size={16} />
                      </div>
                      <div className="text-sm font-bold text-textPrimary">通知設定</div>
                    </div>
                    <ChevronRight size={18} className="text-textTertiary shrink-0" />
                  </div>
                </div>
              </div>

              {/* 4. プライバシー セクション */}
              <div>
                <div className="text-xs font-bold text-textTertiary px-1 mb-2">プライバシー</div>
                <div className="bg-surface border border-borderSubtle rounded-2xl overflow-hidden shadow-xs">
                  <div 
                    onClick={() => setActiveSettingsModal('privacy')}
                    className="p-4 flex items-center justify-between cursor-pointer hover:bg-surfaceSecondary/50 active:bg-surfaceSecondary transition"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-surfaceSecondary text-textSecondary flex items-center justify-center shrink-0">
                        <Shield size={16} />
                      </div>
                      <div className="text-sm font-bold text-textPrimary">プライバシー設定</div>
                    </div>
                    <ChevronRight size={18} className="text-textTertiary shrink-0" />
                  </div>
                </div>
              </div>

              {/* 5. その他 セクション */}
              <div>
                <div className="text-xs font-bold text-textTertiary px-1 mb-2">その他</div>
                <div className="bg-surface border border-borderSubtle rounded-2xl overflow-hidden shadow-xs divide-y divide-borderSubtle">
                  <div 
                    onClick={() => setActiveSettingsModal('terms')}
                    className="p-4 flex items-center justify-between cursor-pointer hover:bg-surfaceSecondary/50 active:bg-surfaceSecondary transition"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-surfaceSecondary text-textSecondary flex items-center justify-center shrink-0">
                        <FileText size={16} />
                      </div>
                      <div className="text-sm font-medium text-textPrimary">利用規約</div>
                    </div>
                    <ChevronRight size={18} className="text-textTertiary shrink-0" />
                  </div>

                  <div 
                    onClick={() => setActiveSettingsModal('policy')}
                    className="p-4 flex items-center justify-between cursor-pointer hover:bg-surfaceSecondary/50 active:bg-surfaceSecondary transition"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-surfaceSecondary text-textSecondary flex items-center justify-center shrink-0">
                        <FileText size={16} />
                      </div>
                      <div className="text-sm font-medium text-textPrimary">プライバシーポリシー</div>
                    </div>
                    <ChevronRight size={18} className="text-textTertiary shrink-0" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ========================================================= */}
      {/* 設定用モーダル（自然に拡張可能と感じられるUI） */}
      {/* ========================================================= */}

      {/* 1. アカウント作成モーダル */}
      {activeSettingsModal === 'account' && (
        <div className="absolute inset-0 bg-black/40 backdrop-blur-xs flex items-end sm:items-center justify-center z-50 p-3">
          <div className="w-full max-w-[400px] bg-surface rounded-3xl p-5 border border-borderSubtle shadow-2xl animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-base font-black text-textPrimary">アカウントを作成・ログイン</h3>
              <button onClick={() => setActiveSettingsModal('none')} className="text-textTertiary hover:text-textPrimary p-1">
                <X size={20} />
              </button>
            </div>
            <p className="text-xs text-textSecondary mb-5 leading-relaxed">
              アカウントを作成すると、スマホの機種変更や他の端末でもこれまでの実験データ・発見ログを引き継げます。
            </p>

            <div className="space-y-2.5 mb-4">
              <button 
                onClick={() => {
                  alert('（アカウント作成機能は今後のアップデートで追加予定です）');
                }}
                className="w-full bg-surface border border-borderSubtle hover:border-accent p-3.5 rounded-2xl flex items-center justify-between text-left text-xs font-bold text-textPrimary transition shadow-xs"
              >
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-full bg-surfaceSecondary flex items-center justify-center">
                    <Mail size={14} className="text-textSecondary" />
                  </div>
                  <span>メールアドレスでアカウント作成</span>
                </div>
                <ChevronRight size={16} className="text-textTertiary" />
              </button>

              <button 
                onClick={() => {
                  alert('（Googleログイン機能は今後のアップデートで追加予定です）');
                }}
                className="w-full bg-surface border border-borderSubtle hover:border-accent p-3.5 rounded-2xl flex items-center justify-between text-left text-xs font-bold text-textPrimary transition shadow-xs"
              >
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-full bg-surfaceSecondary flex items-center justify-center">
                    <span className="font-bold text-xs text-textSecondary">G</span>
                  </div>
                  <span>Googleアカウントでログイン</span>
                </div>
                <ChevronRight size={16} className="text-textTertiary" />
              </button>
            </div>

            <button 
              onClick={() => setActiveSettingsModal('none')}
              className="w-full py-3 bg-surfaceSecondary hover:bg-borderSubtle text-textSecondary text-xs font-bold rounded-xl transition"
            >
              閉じる
            </button>
          </div>
        </div>
      )}

      {/* 2. LINE連携モーダル */}
      {activeSettingsModal === 'line' && (
        <div className="absolute inset-0 bg-black/40 backdrop-blur-xs flex items-end sm:items-center justify-center z-50 p-3">
          <div className="w-full max-w-[400px] bg-surface rounded-3xl p-5 border border-borderSubtle shadow-2xl animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-full bg-[#06C755] text-white flex items-center justify-center">
                  <MessageCircle size={14} />
                </div>
                <h3 className="text-base font-black text-textPrimary">LINE連携</h3>
              </div>
              <button onClick={() => setActiveSettingsModal('none')} className="text-textTertiary hover:text-textPrimary p-1">
                <X size={20} />
              </button>
            </div>
            
            <p className="text-xs text-textSecondary mb-4 leading-relaxed">
              公式LINEと連携すると、毎日おすすめの実験やリマインドをLINEメッセージで直接受け取ることができます。
            </p>

            <div className="bg-surfaceSecondary/70 rounded-2xl p-3.5 mb-5 space-y-2 text-xs text-textSecondary">
              <div className="flex items-center gap-2">
                <Check size={14} className="text-[#06C755]" />
                <span>毎日の5分実験がLINEに届く</span>
              </div>
              <div className="flex items-center gap-2">
                <Check size={14} className="text-[#06C755]" />
                <span>新しい発見やレポートの通知を受け取れる</span>
              </div>
            </div>

            <button 
              onClick={() => {
                alert('（公式LINE連携機能は今後のアップデートで追加予定です）');
              }}
              className="w-full py-3.5 bg-[#06C755] hover:bg-[#05B34C] text-white text-xs font-bold rounded-2xl transition shadow-md shadow-[#06C755]/20 mb-2"
            >
              公式LINEと連携する
            </button>
            <button 
              onClick={() => setActiveSettingsModal('none')}
              className="w-full py-2.5 text-textTertiary text-xs font-medium"
            >
              閉じる
            </button>
          </div>
        </div>
      )}

      {/* 3. 通知設定モーダル */}
      {activeSettingsModal === 'notification' && (
        <div className="absolute inset-0 bg-black/40 backdrop-blur-xs flex items-end sm:items-center justify-center z-50 p-3">
          <div className="w-full max-w-[400px] bg-surface rounded-3xl p-5 border border-borderSubtle shadow-2xl animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-base font-black text-textPrimary">通知設定</h3>
              <button onClick={() => setActiveSettingsModal('none')} className="text-textTertiary hover:text-textPrimary p-1">
                <X size={20} />
              </button>
            </div>
            <p className="text-xs text-textSecondary mb-4">
              受け取りたい通知のオン・オフを設定できます。
            </p>

            <div className="space-y-3 mb-5">
              <div className="flex items-center justify-between p-3 bg-surfaceSecondary/50 rounded-2xl">
                <div>
                  <div className="text-xs font-bold text-textPrimary">今日の実験のリマインド</div>
                  <div className="text-[11px] text-textTertiary">毎日 18:00 にお知らせ</div>
                </div>
                <button 
                  onClick={() => setNotifyDailyReminder(!notifyDailyReminder)}
                  className={`w-11 h-6 rounded-full transition p-0.5 flex items-center ${notifyDailyReminder ? 'bg-accent justify-end' : 'bg-borderStrong justify-start'}`}
                >
                  <div className="w-5 h-5 rounded-full bg-white shadow-xs"></div>
                </button>
              </div>

              <div className="flex items-center justify-between p-3 bg-surfaceSecondary/50 rounded-2xl">
                <div>
                  <div className="text-xs font-bold text-textPrimary">実験を続けるための通知</div>
                  <div className="text-[11px] text-textTertiary">目標達成や変化のお知らせ</div>
                </div>
                <button 
                  onClick={() => setNotifyStreak(!notifyStreak)}
                  className={`w-11 h-6 rounded-full transition p-0.5 flex items-center ${notifyStreak ? 'bg-accent justify-end' : 'bg-borderStrong justify-start'}`}
                >
                  <div className="w-5 h-5 rounded-full bg-white shadow-xs"></div>
                </button>
              </div>

              <div className="flex items-center justify-between p-3 bg-surfaceSecondary/50 rounded-2xl">
                <div>
                  <div className="text-xs font-bold text-textPrimary">LINE通知</div>
                  <div className="text-[11px] text-textTertiary">公式LINEからメッセージ通知</div>
                </div>
                <button 
                  onClick={() => setNotifyLine(!notifyLine)}
                  className={`w-11 h-6 rounded-full transition p-0.5 flex items-center ${notifyLine ? 'bg-accent justify-end' : 'bg-borderStrong justify-start'}`}
                >
                  <div className="w-5 h-5 rounded-full bg-white shadow-xs"></div>
                </button>
              </div>

              <div className="flex items-center justify-between p-3 bg-surfaceSecondary/50 rounded-2xl">
                <div>
                  <div className="text-xs font-bold text-textPrimary">アプリ内通知</div>
                  <div className="text-[11px] text-textTertiary">新分野やおすすめの表示</div>
                </div>
                <button 
                  onClick={() => setNotifyInApp(!notifyInApp)}
                  className={`w-11 h-6 rounded-full transition p-0.5 flex items-center ${notifyInApp ? 'bg-accent justify-end' : 'bg-borderStrong justify-start'}`}
                >
                  <div className="w-5 h-5 rounded-full bg-white shadow-xs"></div>
                </button>
              </div>
            </div>

            <button 
              onClick={() => setActiveSettingsModal('none')}
              className="w-full py-3.5 bg-accent hover:bg-accentDark text-accentText text-xs font-bold rounded-2xl transition"
            >
              設定を保存する
            </button>
          </div>
        </div>
      )}

      {/* 4. プライバシー設定モーダル */}
      {activeSettingsModal === 'privacy' && (
        <div className="absolute inset-0 bg-black/40 backdrop-blur-xs flex items-end sm:items-center justify-center z-50 p-3">
          <div className="w-full max-w-[400px] bg-surface rounded-3xl p-5 border border-borderSubtle shadow-2xl animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-base font-black text-textPrimary">プライバシー設定</h3>
              <button onClick={() => setActiveSettingsModal('none')} className="text-textTertiary hover:text-textPrimary p-1">
                <X size={20} />
              </button>
            </div>
            
            <div className="space-y-2.5 mb-5">
              <div className="p-3 bg-surfaceSecondary/50 rounded-2xl flex items-center justify-between">
                <div>
                  <div className="text-xs font-bold text-textPrimary">利用データの扱い</div>
                  <div className="text-[11px] text-textTertiary">学校や第三者に共有されません</div>
                </div>
                <span className="text-[11px] font-semibold text-accent">保護中</span>
              </div>

              <div className="p-3 bg-surfaceSecondary/50 rounded-2xl flex items-center justify-between">
                <div>
                  <div className="text-xs font-bold text-textPrimary">分析データの利用</div>
                  <div className="text-[11px] text-textTertiary">AI仮説生成のための匿名統計</div>
                </div>
                <button 
                  onClick={() => setAllowAnalytics(!allowAnalytics)}
                  className={`w-11 h-6 rounded-full transition p-0.5 flex items-center ${allowAnalytics ? 'bg-accent justify-end' : 'bg-borderStrong justify-start'}`}
                >
                  <div className="w-5 h-5 rounded-full bg-white shadow-xs"></div>
                </button>
              </div>

              <div className="p-3 bg-surfaceSecondary/50 rounded-2xl flex items-center justify-between">
                <div>
                  <div className="text-xs font-bold text-textPrimary">アカウント情報</div>
                  <div className="text-[11px] text-textTertiary">未登録（ゲスト利用中）</div>
                </div>
              </div>

              <button 
                onClick={() => {
                  if (confirm('すべての実験データとシグナルを初期化しますか？')) {
                    setCompletedCount(0);
                    alert('データを初期化しました');
                    setActiveSettingsModal('none');
                  }
                }}
                className="w-full p-3 bg-red-50 hover:bg-red-100 text-red-600 rounded-2xl text-left text-xs font-bold transition flex items-center justify-between mt-2"
              >
                <span>データの初期化・削除</span>
                <span className="text-[10px] text-red-400">リセット</span>
              </button>
            </div>

            <button 
              onClick={() => setActiveSettingsModal('none')}
              className="w-full py-3 bg-surfaceSecondary hover:bg-borderSubtle text-textSecondary text-xs font-bold rounded-xl transition"
            >
              閉じる
            </button>
          </div>
        </div>
      )}

      {/* 5. 利用規約 / プライバシーポリシー モーダル */}
      {(activeSettingsModal === 'terms' || activeSettingsModal === 'policy') && (
        <div className="absolute inset-0 bg-black/40 backdrop-blur-xs flex items-end sm:items-center justify-center z-50 p-3">
          <div className="w-full max-w-[400px] bg-surface rounded-3xl p-5 border border-borderSubtle shadow-2xl animate-in slide-in-from-bottom duration-200 max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-base font-black text-textPrimary">
                {activeSettingsModal === 'terms' ? '利用規約' : 'プライバシーポリシー'}
              </h3>
              <button onClick={() => setActiveSettingsModal('none')} className="text-textTertiary hover:text-textPrimary p-1">
                <X size={20} />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto text-xs text-textSecondary leading-relaxed space-y-3 mb-4 pr-1">
              {activeSettingsModal === 'terms' ? (
                <>
                  <p>本サービス「興味発見」は、高校生が日常の小さな行動実験を通して自然な興味・関心を見つけるための自己探索支援サービスです。</p>
                  <p>ユーザーは自己の責任において実験を行い、本サービスを利用するものとします。</p>
                </>
              ) : (
                <>
                  <p>本サービスでは、興味分析・仮説生成の目的でのみ入力データおよび実験ログを利用します。</p>
                  <p>取得したデータが学校・教員・保護者・広告会社等の第三者に無断で提供されることは一切ありません。</p>
                </>
              )}
            </div>
            <button 
              onClick={() => setActiveSettingsModal('none')}
              className="w-full py-3 bg-surfaceSecondary hover:bg-borderSubtle text-textSecondary text-xs font-bold rounded-xl transition mt-auto"
            >
              閉じる
            </button>
          </div>
        </div>
      )}

      {/* ========================================================= */}
      {/* ボトムナビゲーションバー（4タブ構成は一切変更なし） */}
      {/* ========================================================= */}
      <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[440px] bg-surface/95 backdrop-blur-md border-t border-borderSubtle h-16 flex items-center justify-around px-3 z-40">
        {[
          { id: 'home', label: 'ホーム', icon: HomeIcon },
          { id: 'explore', label: '分野をみる', icon: Compass },
          { id: 'log', label: 'じぶんログ', icon: BarChart3 },
          { id: 'settings', label: '設定', icon: SettingsIcon },
        ].map(item => {
          const Icon = item.icon;
          const isActive = currentTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setCurrentTab(item.id as any)}
              className={`flex flex-col items-center justify-center flex-1 py-1 transition ${
                isActive ? 'text-accent font-black scale-105' : 'text-textTertiary font-medium'
              }`}
            >
              <Icon size={20} strokeWidth={isActive ? 2.5 : 1.8} />
              <span className="text-[10px] mt-1">{item.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
