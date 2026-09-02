import { useState, useEffect } from 'react';
import { 
  INITIAL_EXPERIMENTS, 
  INITIAL_DOMAINS, 
  SIGNAL_LABELS, 
  Experiment
} from './data';
import { 
  Home as HomeIcon, 
  Search, 
  Compass, 
  BarChart2, 
  Settings as SettingsIcon, 
  Clock, 
  CheckCircle2, 
  ArrowRight, 
  ChevronDown, 
  ChevronUp, 
  Sparkles,
  ShieldCheck
} from 'lucide-react';

export default function App() {
  const [currentTab, setCurrentTab] = useState<'home' | 'discover' | 'explore' | 'report' | 'settings'>('home');
  const [completedCount, setCompletedCount] = useState(2);
  const [activeExperiment, setActiveExperiment] = useState<Experiment | null>(null);
  const [screen, setScreen] = useState<'main' | 'detail' | 'running' | 'reflection' | 'result'>('main');

  // タイマー状態
  const [timerSeconds, setTimerSeconds] = useState(0);
  const [isTimerRunning, setIsTimerRunning] = useState(false);

  // 振り返り評価
  const [enjoyment, setEnjoyment] = useState<number | null>(null);
  const [curiosity, setCuriosity] = useState<number | null>(null);
  const [retryIntent, setRetryIntent] = useState<number | null>(null);

  // Exploreフィルター
  const [exploreFilter, setExploreFilter] = useState<'ALL' | 'UNEXPLORED' | 'EXPLORED' | 'TRIED' | 'DIVE_CANDIDATE'>('ALL');
  // Discoverアコーディオン
  const [isEvidenceOpen, setIsEvidenceOpen] = useState(false);
  // Settings通知トグル
  const [reminderEnabled, setReminderEnabled] = useState(true);

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

  // 1. 実験詳細画面
  if (screen === 'detail' && activeExperiment) {
    return (
      <div className="flex-1 flex flex-col h-full bg-background p-6 overflow-y-auto">
        <button onClick={handleBackToMain} className="text-textSecondary text-sm font-medium self-start mb-6">
          ← 戻る
        </button>
        <div className="flex gap-2 mb-4">
          <span className="bg-badgeBg text-badgeText text-xs font-medium px-2.5 py-1 rounded-badge">
            {activeExperiment.plannedMinutes}分
          </span>
          <span className="bg-accentSoft text-accent text-xs font-semibold px-2.5 py-1 rounded-badge">
            {SIGNAL_LABELS[activeExperiment.actionType]}
          </span>
        </div>
        <h1 className="text-2xl font-bold text-textPrimary leading-tight mb-3">
          {activeExperiment.title}
        </h1>
        <p className="text-textSecondary text-sm leading-relaxed mb-6">
          {activeExperiment.description}
        </p>

        <div className="bg-surface border border-borderSubtle rounded-insight p-4 mb-3 shadow-sm">
          <div className="text-xs font-semibold text-textSecondary mb-1">なぜこの実験？</div>
          <div className="text-sm text-textPrimary leading-relaxed">{activeExperiment.reason}</div>
        </div>

        <div className="bg-surfaceSecondary border border-borderSubtle rounded-insight p-4 mb-8">
          <div className="text-xs font-semibold text-textSecondary mb-1">見てみたいこと</div>
          <div className="text-sm text-textPrimary leading-relaxed">{activeExperiment.testedHypothesis}</div>
        </div>

        <button 
          onClick={handleBeginTimer}
          className="w-full bg-accent hover:bg-accentDark active:scale-[0.98] transition text-accentText font-semibold py-4 rounded-button shadow-md mt-auto"
        >
          実験をスタートする
        </button>
      </div>
    );
  }

  // 2. 実験中タイマー画面
  if (screen === 'running' && activeExperiment) {
    const min = Math.floor(timerSeconds / 60);
    const sec = timerSeconds % 60;
    const timeStr = `${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;

    return (
      <div className="flex-1 flex flex-col h-full bg-background p-6 justify-between items-center text-center">
        <div>
          <div className="flex justify-center gap-2 mb-3 mt-4">
            <span className="bg-badgeBg text-badgeText text-xs font-medium px-2.5 py-1 rounded-badge">
              {activeExperiment.plannedMinutes}分
            </span>
            <span className="bg-accentSoft text-accent text-xs font-semibold px-2.5 py-1 rounded-badge">
              {SIGNAL_LABELS[activeExperiment.actionType]}
            </span>
          </div>
          <h2 className="text-xl font-bold text-textPrimary mb-2">{activeExperiment.title}</h2>
          <p className="text-xs text-textSecondary leading-relaxed px-4">{activeExperiment.description}</p>
        </div>

        <div className="bg-surface border border-borderSubtle rounded-3xl p-8 shadow-sm flex flex-col items-center">
          <span className="text-[11px] font-bold tracking-wider text-textTertiary uppercase mb-2">経過時間</span>
          <span className="text-5xl font-mono font-bold text-accent mb-3">{timeStr}</span>
          <span className="text-xs text-textSecondary">やってみて「心地いい・楽しい」と感じるか意識してみよう</span>
        </div>

        <div className="w-full">
          <button 
            onClick={handleFinishTimer}
            className="w-full bg-accent hover:bg-accentDark active:scale-[0.98] transition text-accentText font-semibold py-4 rounded-button shadow-md mb-3"
          >
            実験を完了する
          </button>
          <button 
            onClick={handleBackToMain}
            className="text-textSecondary text-sm font-medium py-2"
          >
            中断してホームに戻る
          </button>
        </div>
      </div>
    );
  }

  // 3. 振り返り画面
  if (screen === 'reflection') {
    const isValid = enjoyment !== null && curiosity !== null && retryIntent !== null;

    return (
      <div className="flex-1 flex flex-col h-full bg-background p-6 overflow-y-auto">
        <h1 className="text-2xl font-bold text-textPrimary mb-1">やってみてどうだった？</h1>
        <p className="text-xs text-textSecondary mb-6">今の直感を 1〜5 で教えてください。</p>

        <div className="space-y-6 mb-8">
          <div>
            <p className="text-sm font-semibold text-textPrimary mb-2">1. やっていて楽しかった？</p>
            <div className="flex justify-between gap-2">
              {[1, 2, 3, 4, 5].map(score => (
                <button
                  key={score}
                  onClick={() => setEnjoyment(score)}
                  className={`flex-1 h-12 rounded-selector border font-bold text-base transition ${
                    enjoyment === score 
                      ? 'bg-accent text-accentText border-accent shadow-sm' 
                      : 'bg-surface text-textPrimary border-borderSubtle'
                  }`}
                >
                  {score}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="text-sm font-semibold text-textPrimary mb-2">2. もっと知りたくなった？</p>
            <div className="flex justify-between gap-2">
              {[1, 2, 3, 4, 5].map(score => (
                <button
                  key={score}
                  onClick={() => setCuriosity(score)}
                  className={`flex-1 h-12 rounded-selector border font-bold text-base transition ${
                    curiosity === score 
                      ? 'bg-accent text-accentText border-accent shadow-sm' 
                      : 'bg-surface text-textPrimary border-borderSubtle'
                  }`}
                >
                  {score}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="text-sm font-semibold text-textPrimary mb-2">3. 似たようなことをまたやってみたい？</p>
            <div className="flex justify-between gap-2">
              {[1, 2, 3, 4, 5].map(score => (
                <button
                  key={score}
                  onClick={() => setRetryIntent(score)}
                  className={`flex-1 h-12 rounded-selector border font-bold text-base transition ${
                    retryIntent === score 
                      ? 'bg-accent text-accentText border-accent shadow-sm' 
                      : 'bg-surface text-textPrimary border-borderSubtle'
                  }`}
                >
                  {score}
                </button>
              ))}
            </div>
          </div>
        </div>

        <button 
          onClick={handleSubmitReflection}
          disabled={!isValid}
          className={`w-full py-4 rounded-button font-semibold shadow-md transition mt-auto ${
            isValid 
              ? 'bg-accent hover:bg-accentDark text-accentText active:scale-[0.98]' 
              : 'bg-borderStrong text-textTertiary cursor-not-allowed'
          }`}
        >
          完了
        </button>
      </div>
    );
  }

  // 4. 発見結果画面
  if (screen === 'result') {
    return (
      <div className="flex-1 flex flex-col h-full bg-background p-6 overflow-y-auto">
        <h1 className="text-2xl font-bold text-textPrimary mb-1">見えてきたこと</h1>
        <p className="text-xs text-textSecondary mb-6">これまでの実験から見えてきた行動パターンです。</p>

        <div className="bg-surface border border-borderSubtle rounded-card p-5 mb-4 shadow-sm">
          <div className="text-[11px] font-bold tracking-wider text-textTertiary uppercase mb-1">行動の観察</div>
          <div className="text-sm font-medium text-textPrimary leading-relaxed">
            あなたは「構造を分析して違いを見つける」実験で最も高い没頭度を示しました。
          </div>
        </div>

        <div className="bg-surfaceSecondary border border-borderSubtle rounded-card p-5 mb-6">
          <div className="text-[11px] font-bold tracking-wider text-accent uppercase mb-1">現在の仮説</div>
          <div className="text-sm font-semibold text-textPrimary leading-relaxed mb-2">
            情報やデザインの配置・ルールに気づき、仕組みを解き明かすことに自然な興味があるかもしれません。
          </div>
          <div className="text-[11px] text-textTertiary">※これは性格診断ではなく、直近の行動から見えた傾向の仮説です。</div>
        </div>

        <button 
          onClick={handleBackToMain}
          className="w-full bg-accent hover:bg-accentDark text-accentText font-semibold py-4 rounded-button shadow-md mt-auto active:scale-[0.98] transition"
        >
          ホームに戻る
        </button>
      </div>
    );
  }

  // 5. メイン画面（ボトムナビゲーション付き）
  return (
    <div className="flex-1 flex flex-col h-full justify-between">
      {/* 画面コンテンツ */}
      <div className="flex-1 overflow-y-auto p-5 pb-20">
        
        {/* TAB 1: ホーム */}
        {currentTab === 'home' && (
          <div>
            <div className="flex items-center justify-between mb-4">
              <div>
                <h1 className="text-2xl font-bold text-textPrimary">今日をひらく</h1>
                <p className="text-xs text-textSecondary mt-0.5">5〜15分の小さな実験で、自分の興味を発見しよう</p>
              </div>
            </div>

            {/* 今日の状態カード */}
            <div className="bg-surface border border-borderSubtle rounded-card p-4 mb-5 shadow-sm">
              <div className="flex items-center gap-3">
                <div className={`w-9 h-9 rounded-full flex items-center justify-center ${completedCount > 2 ? 'bg-accentSoft text-accent' : 'bg-surfaceSecondary text-textTertiary'}`}>
                  {completedCount > 2 ? <CheckCircle2 size={20} /> : <Clock size={20} />}
                </div>
                <div>
                  <div className="text-xs text-textSecondary font-medium">今日の実験</div>
                  <div className="text-sm font-bold text-textPrimary">
                    {completedCount > 2 ? '今日の実験を完了しました！🎉' : 'まだ未実施です（下の3つから選んでみよう）'}
                  </div>
                </div>
              </div>
            </div>

            {/* 3つのExperimentタスクリスト */}
            <div className="mb-6">
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-base font-bold text-textPrimary">今日のおすすめ実験 3選</h2>
                <span className="text-xs text-textTertiary">所要時間 5〜10分</span>
              </div>
              
              <div className="space-y-3">
                {INITIAL_EXPERIMENTS.map((exp, index) => (
                  <div 
                    key={exp.id}
                    onClick={() => handleStartExperiment(exp)}
                    className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm hover:border-accent transition cursor-pointer active:scale-[0.99]"
                  >
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex gap-2 items-center">
                        <span className="bg-badgeBg text-badgeText text-[11px] font-medium px-2 py-0.5 rounded-badge">
                          {exp.plannedMinutes}分
                        </span>
                        <span className="bg-accentSoft text-accent text-[11px] font-semibold px-2 py-0.5 rounded-badge">
                          {SIGNAL_LABELS[exp.actionType]}
                        </span>
                      </div>
                      <span className="text-xs text-textTertiary font-mono">#{index + 1}</span>
                    </div>
                    <h3 className="text-sm font-bold text-textPrimary mb-1">{exp.title}</h3>
                    <p className="text-xs text-textSecondary line-clamp-2 leading-relaxed">{exp.description}</p>
                    <div className="flex items-center justify-end gap-1 text-accent text-xs font-semibold mt-3">
                      <span>やってみる</span>
                      <ArrowRight size={13} />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 今週の進捗 & 行動シグナル */}
            <div className="bg-surface border border-borderSubtle rounded-card p-4 mb-4 shadow-sm">
              <div className="text-xs font-semibold text-textSecondary mb-2">
                今週 <span className="text-accent font-bold">{completedCount}件</span> の実験を完了
              </div>
              <div className="flex flex-wrap gap-2">
                <span className="bg-surface border border-borderSubtle px-2.5 py-1 rounded-badge text-xs font-semibold text-accent">
                  分析する ↑
                </span>
                <span className="bg-surface border border-borderSubtle px-2.5 py-1 rounded-badge text-xs font-medium text-textPrimary">
                  つくる
                </span>
                <span className="bg-surface border border-borderSubtle px-2.5 py-1 rounded-badge text-xs font-semibold text-accent">
                  比べる ↑
                </span>
              </div>
            </div>

            {/* 見えてきた傾向インサイト */}
            <div 
              onClick={() => setCurrentTab('discover')}
              className="bg-surfaceSecondary border border-borderSubtle rounded-insight p-4 cursor-pointer hover:border-accent transition"
            >
              <div className="flex items-center justify-between mb-1.5">
                <span className="text-xs font-bold text-textSecondary flex items-center gap-1">
                  <Sparkles size={14} className="text-accent" /> 見えてきた傾向
                </span>
                <span className="text-xs text-accent font-medium">発見を見る →</span>
              </div>
              <p className="text-xs text-textPrimary leading-relaxed">
                情報がどう整理されているかに気づき、より良くする方法を考えることが好きなようです。
              </p>
            </div>
          </div>
        )}

        {/* TAB 2: 発見 */}
        {currentTab === 'discover' && (
          <div>
            <h1 className="text-2xl font-bold text-textPrimary mb-1">発見</h1>
            <p className="text-xs text-textSecondary mb-5">あなたの行動から見えてきた関心のサイン</p>

            <div className="space-y-4">
              <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm">
                <div className="text-xs font-bold text-accent mb-1.5">💡 今わかってきたこと</div>
                <div className="text-sm font-semibold text-textPrimary mb-1">UI・情報設計への興味</div>
                <p className="text-xs text-textSecondary leading-relaxed">
                  情報がどう整理されているかに気づき、より良くする方法を考えることが好きなようです。
                </p>
              </div>

              <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm">
                <div className="text-xs font-bold text-textSecondary mb-1.5">🎯 いま確かめていること</div>
                <p className="text-xs text-textPrimary leading-relaxed">
                  観察だけでなく、実際に手を動かして形にする（CREATE）ことにも興味が広がるかを確かめています。
                </p>
              </div>

              <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm">
                <div className="text-xs font-bold text-textSecondary mb-1.5">📈 以前と変わってきたこと</div>
                <p className="text-xs text-textPrimary leading-relaxed">
                  先週と比べて「比べる」「分析する」シグナルの評価が急上昇しています。
                </p>
              </div>

              {/* 根拠アコーディオン */}
              <div className="bg-surfaceSecondary border border-borderSubtle rounded-card overflow-hidden">
                <button 
                  onClick={() => setIsEvidenceOpen(!isEvidenceOpen)}
                  className="w-full p-4 flex items-center justify-between text-left text-xs font-bold text-textSecondary"
                >
                  <span>❓ なぜそう表示された？（判定の根拠）</span>
                  {isEvidenceOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                </button>
                {isEvidenceOpen && (
                  <div className="px-4 pb-4 text-xs text-textSecondary border-t border-borderSubtle pt-3 leading-relaxed">
                    直近3回の実験において、「楽しかった」「またやりたい」のスコアが平均4.5点以上であり、予定時間より長く観察を続けた行動ログに基づいています。
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: 探索 */}
        {currentTab === 'explore' && (
          <div>
            <h1 className="text-2xl font-bold text-textPrimary mb-1">分野を探索する</h1>
            <p className="text-xs text-textSecondary mb-4">まだ試していない領域に小さく触れてみよう</p>

            {/* フィルター */}
            <div className="flex gap-1.5 overflow-x-auto pb-2 mb-4">
              {[
                { id: 'ALL', label: 'すべて' },
                { id: 'UNEXPLORED', label: '未探索' },
                { id: 'EXPLORED', label: 'Explore済み' },
                { id: 'TRIED', label: 'Try済み' },
                { id: 'DIVE_CANDIDATE', label: 'Dive候補 🔥' },
              ].map(f => (
                <button
                  key={f.id}
                  onClick={() => setExploreFilter(f.id as any)}
                  className={`px-3 py-1.5 rounded-badge text-xs font-medium whitespace-nowrap transition ${
                    exploreFilter === f.id 
                      ? 'bg-accent text-accentText' 
                      : 'bg-surface border border-borderSubtle text-textSecondary'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>

            {/* 分野カード一覧 */}
            <div className="space-y-3">
              {INITIAL_DOMAINS
                .filter(d => exploreFilter === 'ALL' || d.status === exploreFilter)
                .map(domain => (
                  <div key={domain.id} className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <span className="text-xl">{domain.icon}</span>
                        <h3 className="text-sm font-bold text-textPrimary">{domain.name}</h3>
                      </div>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-badge ${
                        domain.status === 'DIVE_CANDIDATE' ? 'bg-amber-100 text-amber-800' :
                        domain.status === 'TRIED' ? 'bg-accentSoft text-accent' :
                        domain.status === 'EXPLORED' ? 'bg-blue-50 text-blue-700' :
                        'bg-gray-100 text-gray-600'
                      }`}>
                        {domain.status === 'DIVE_CANDIDATE' ? 'Dive候補 🔥' :
                         domain.status === 'TRIED' ? 'Try済み' :
                         domain.status === 'EXPLORED' ? 'Explore済み' : '未探索'}
                      </span>
                    </div>
                    <p className="text-xs text-textSecondary mb-3 leading-relaxed">{domain.description}</p>
                    <div className="flex items-center justify-between text-xs text-textTertiary pt-2 border-t border-borderSubtle">
                      <span>実験 {domain.completedCount}/{domain.totalCount} 件</span>
                      <span className="text-accent font-medium">{domain.recommendedSignal}</span>
                    </div>
                  </div>
                ))}
            </div>
          </div>
        )}

        {/* TAB 4: レポート */}
        {currentTab === 'report' && (
          <div>
            <h1 className="text-2xl font-bold text-textPrimary mb-1">行動レポート</h1>
            <p className="text-xs text-textSecondary mb-5">過去の実験ログから読み解くあなたの変化</p>

            <div className="grid grid-cols-2 gap-3 mb-4">
              <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm text-center">
                <div className="text-2xl font-bold text-accent">{completedCount + 2}</div>
                <div className="text-xs text-textSecondary mt-0.5">完了した実験</div>
              </div>
              <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm text-center">
                <div className="text-2xl font-bold text-accent">28分</div>
                <div className="text-xs text-textSecondary mt-0.5">合計時間</div>
              </div>
            </div>

            <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm mb-4">
              <div className="text-xs font-bold text-textSecondary mb-1">今週もっとも強かったシグナル</div>
              <div className="text-base font-bold text-accent mb-2">🔥 分析する (ANALYZE)</div>
              <p className="text-xs text-textSecondary leading-relaxed">
                日常の身近なUIやアプリの意図を考察する実験で、平均満足度 4.8 点を記録しました。
              </p>
            </div>

            {/* シグナル分布バー */}
            <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm mb-4">
              <div className="text-xs font-bold text-textSecondary mb-3">シグナル分布</div>
              <div className="space-y-2">
                {[
                  { name: '分析する', count: 4, width: '80%' },
                  { name: '比べる', count: 3, width: '60%' },
                  { name: 'つくる', count: 2, width: '40%' },
                  { name: '整理する', count: 1, width: '20%' },
                ].map(s => (
                  <div key={s.name}>
                    <div className="flex justify-between text-xs text-textPrimary mb-1">
                      <span>{s.name}</span>
                      <span className="font-mono text-textTertiary">{s.count}回</span>
                    </div>
                    <div className="w-full bg-surfaceSecondary h-2 rounded-full overflow-hidden">
                      <div className="bg-accent h-full rounded-full" style={{ width: s.width }}></div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="bg-surfaceSecondary border border-borderSubtle rounded-card p-4">
              <div className="text-xs font-bold text-textSecondary mb-1">🌱 過去の自分との変化</div>
              <p className="text-xs text-textPrimary leading-relaxed">
                先月は「つくる」中心でしたが、今月は「仕組みを見る」「比べる」ことへの関心が高まっています。
              </p>
            </div>
          </div>
        )}

        {/* TAB 5: 設定 */}
        {currentTab === 'settings' && (
          <div>
            <h1 className="text-2xl font-bold text-textPrimary mb-1">設定・データ管理</h1>
            <p className="text-xs text-textSecondary mb-5">プライバシーとリマインドの管理</p>

            <div className="space-y-4">
              <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm">
                <div className="text-xs font-bold text-textSecondary mb-1">📊 保存されている行動シグナル</div>
                <div className="text-sm font-bold text-textPrimary mb-1">10件の行動ログを記録中</div>
                <p className="text-xs text-textSecondary leading-relaxed">
                  性格診断ではなく、あなたが実際に試した5〜15分の直感から分析しています。
                </p>
              </div>

              <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm flex items-center justify-between">
                <div>
                  <div className="text-sm font-bold text-textPrimary">毎日の5分リマインド</div>
                  <div className="text-xs text-textSecondary">毎日 18:00 に通知</div>
                </div>
                <button 
                  onClick={() => setReminderEnabled(!reminderEnabled)}
                  className={`w-12 h-6 rounded-full transition p-1 flex items-center ${reminderEnabled ? 'bg-accent justify-end' : 'bg-borderStrong justify-start'}`}
                >
                  <div className="w-4 h-4 rounded-full bg-white shadow-sm"></div>
                </button>
              </div>

              <div className="bg-surface border border-borderSubtle rounded-card p-4 shadow-sm">
                <div className="flex items-center gap-2 text-xs font-bold text-accent mb-1.5">
                  <ShieldCheck size={16} /> プライバシーと安全への約束
                </div>
                <p className="text-xs text-textSecondary leading-relaxed">
                  あなたの実験データや振り返りメモは、学校・先生・広告会社に共有されることは一切ありません。
                </p>
              </div>

              <button 
                onClick={() => {
                  if (confirm('すべての実験データとシグナルを初期化しますか？')) {
                    setCompletedCount(0);
                    alert('データをリセットしました');
                  }
                }}
                className="w-full bg-surface border border-red-200 text-red-600 font-semibold py-3.5 rounded-button hover:bg-red-50 transition text-sm"
              >
                すべてのデータをリセットする
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ボトムナビゲーションバー */}
      <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[440px] bg-surface border-t border-borderSubtle h-16 flex items-center justify-around px-2 z-50">
        {[
          { id: 'home', label: 'ホーム', icon: HomeIcon },
          { id: 'discover', label: '発見', icon: Search },
          { id: 'explore', label: '探索', icon: Compass },
          { id: 'report', label: 'レポート', icon: BarChart2 },
          { id: 'settings', label: '設定', icon: SettingsIcon },
        ].map(item => {
          const Icon = item.icon;
          const isActive = currentTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setCurrentTab(item.id as any)}
              className={`flex flex-col items-center justify-center flex-1 py-1 transition ${
                isActive ? 'text-accent font-bold' : 'text-textTertiary font-normal'
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
