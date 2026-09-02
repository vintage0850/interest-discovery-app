export type BehaviorSignal = 'ANALYZE' | 'CREATE' | 'COMPARE' | 'EXPLAIN' | 'ORGANIZE' | 'EXPLORE' | 'IMPROVE';

export interface SignalTheme {
  label: string;
  shortLabel: string;
  icon: string;
  bgGradient: string;
  cardBg: string;
  accentColor: string;
  badgeBg: string;
  badgeText: string;
}

export const SIGNAL_THEMES: Record<BehaviorSignal, SignalTheme> = {
  ANALYZE: {
    label: '分析・観察',
    shortLabel: '観察',
    icon: '🔍',
    bgGradient: 'from-blue-50 to-indigo-50/50',
    cardBg: 'bg-blue-50/60 border-blue-200/80',
    accentColor: '#2563EB',
    badgeBg: 'bg-blue-100 text-blue-800',
    badgeText: 'text-blue-800'
  },
  CREATE: {
    label: 'つくる',
    shortLabel: 'つくる',
    icon: '🎨',
    bgGradient: 'from-amber-50 to-orange-50/50',
    cardBg: 'bg-amber-50/60 border-amber-200/80',
    accentColor: '#D97706',
    badgeBg: 'bg-amber-100 text-amber-800',
    badgeText: 'text-amber-800'
  },
  COMPARE: {
    label: '比べる',
    shortLabel: '比べる',
    icon: '⚖️',
    bgGradient: 'from-purple-50 to-pink-50/50',
    cardBg: 'bg-purple-50/60 border-purple-200/80',
    accentColor: '#7C3AED',
    badgeBg: 'bg-purple-100 text-purple-800',
    badgeText: 'text-purple-800'
  },
  EXPLAIN: {
    label: '説明する',
    shortLabel: '説明',
    icon: '📝',
    bgGradient: 'from-emerald-50 to-teal-50/50',
    cardBg: 'bg-emerald-50/60 border-emerald-200/80',
    accentColor: '#059669',
    badgeBg: 'bg-emerald-100 text-emerald-800',
    badgeText: 'text-emerald-800'
  },
  ORGANIZE: {
    label: '整理する',
    shortLabel: '整理',
    icon: '🗂️',
    bgGradient: 'from-cyan-50 to-sky-50/50',
    cardBg: 'bg-cyan-50/60 border-cyan-200/80',
    accentColor: '#0891B2',
    badgeBg: 'bg-cyan-100 text-cyan-800',
    badgeText: 'text-cyan-800'
  },
  EXPLORE: {
    label: '調べる',
    shortLabel: '調べる',
    icon: '🧭',
    bgGradient: 'from-rose-50 to-orange-50/50',
    cardBg: 'bg-rose-50/60 border-rose-200/80',
    accentColor: '#E11D48',
    badgeBg: 'bg-rose-100 text-rose-800',
    badgeText: 'text-rose-800'
  },
  IMPROVE: {
    label: '改善する',
    shortLabel: '改善',
    icon: '💡',
    bgGradient: 'from-teal-50 to-emerald-50/50',
    cardBg: 'bg-teal-50/60 border-teal-200/80',
    accentColor: '#0D9488',
    badgeBg: 'bg-teal-100 text-teal-800',
    badgeText: 'text-teal-800'
  }
};

export interface Experiment {
  id: string;
  title: string;
  subtitle: string;
  steps: string[];
  plannedMinutes: number;
  actionType: BehaviorSignal;
  reasonShort: string;
  hypothesisShort: string;
}

export interface DomainField {
  id: string;
  name: string;
  icon: string;
  tagline: string;
  status: 'UNEXPLORED' | 'EXPLORED' | 'TRIED' | 'DIVE_CANDIDATE';
  completedCount: number;
  totalCount: number;
  signalTag: string;
}

export const INITIAL_EXPERIMENTS: Experiment[] = [
  {
    id: 'exp-1',
    title: '好きなゲームのUIを観察する',
    subtitle: '画面のボタン配置の「なぜ？」を見つける',
    steps: [
      'よく遊ぶゲームのホーム画面を開く',
      '一番目立つボタンを1つ見つける',
      '「なぜその位置・色なのか」理由を考えてみる'
    ],
    plannedMinutes: 5,
    actionType: 'ANALYZE',
    reasonShort: '画面のレイアウトや情報整理への興味を確かめるため',
    hypothesisShort: '「なぜ使いやすいか」を考えることに自然と惹かれるか'
  },
  {
    id: 'exp-2',
    title: '3つの図形でミニポスターをつくる',
    subtitle: '○ △ □ だけで感情を表現してみる',
    steps: [
      '紙かスマホのメモ帳を開く',
      '丸・三角・四角の3つだけを描く',
      '「ワクワク」または「静けさ」を表現してみる'
    ],
    plannedMinutes: 10,
    actionType: 'CREATE',
    reasonShort: '限られた条件で手を動かして表現する楽しさを知るため',
    hypothesisShort: '何もないところから形にすることに没頭できるか'
  },
  {
    id: 'exp-3',
    title: 'LINEとインスタの画面を比べる',
    subtitle: '2つのアプリの「検索」の違いを探す',
    steps: [
      'LINEとInstagramの検索タブを交互に開く',
      '画面の並び順や違いを1つ見つける',
      '「なぜこの違いがあるのか」考えてみる'
    ],
    plannedMinutes: 5,
    actionType: 'COMPARE',
    reasonShort: '似ているものの仕組みや目的の違いに気づく力を観察するため',
    hypothesisShort: '使いやすさの違いやターゲットの意図に目が向くか'
  }
];

export const INITIAL_DOMAINS: DomainField[] = [
  {
    id: 'f-1',
    name: 'デザイン & UI',
    icon: '🎨',
    tagline: 'どう見せると使いやすいか・伝わるか',
    status: 'TRIED',
    completedCount: 3,
    totalCount: 5,
    signalTag: '🔍 観察 / 🎨 つくる'
  },
  {
    id: 'f-2',
    name: 'ビジネス & 仕組み',
    icon: '📈',
    tagline: 'どうやって人が集まり、価値が生まれるか',
    status: 'DIVE_CANDIDATE',
    completedCount: 1,
    totalCount: 4,
    signalTag: '⚖️ 比べる / 💡 改善'
  },
  {
    id: 'f-3',
    name: 'テクノロジー & 開発',
    icon: '💻',
    tagline: 'プログラミングやAIで効率化・自動化する',
    status: 'EXPLORED',
    completedCount: 1,
    totalCount: 6,
    signalTag: '🗂️ 整理 / 🎨 つくる'
  },
  {
    id: 'f-4',
    name: 'ことば & ストーリー',
    icon: '📝',
    tagline: '文章や対話で人の心を動かす',
    status: 'UNEXPLORED',
    completedCount: 0,
    totalCount: 4,
    signalTag: '📝 説明'
  },
  {
    id: 'f-5',
    name: 'モノづくり & 空間',
    icon: '📦',
    tagline: '手に取れる形や空間を工夫してつくる',
    status: 'UNEXPLORED',
    completedCount: 0,
    totalCount: 5,
    signalTag: '🎨 つくる / 💡 改善'
  },
  {
    id: 'f-6',
    name: 'サイエンス & 探究',
    icon: '🔬',
    tagline: '自然やデータの法則性を読み解く',
    status: 'UNEXPLORED',
    completedCount: 0,
    totalCount: 4,
    signalTag: '🧭 調べる / 🔍 観察'
  }
];
