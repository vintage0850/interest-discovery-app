export type BehaviorSignal = 'ANALYZE' | 'CREATE' | 'COMPARE' | 'EXPLAIN' | 'ORGANIZE' | 'EXPLORE' | 'IMPROVE';

export const SIGNAL_LABELS: Record<BehaviorSignal, string> = {
  ANALYZE: '分析する',
  CREATE: 'つくる',
  COMPARE: '比べる',
  EXPLAIN: '説明する',
  ORGANIZE: '整理する',
  EXPLORE: '調べる',
  IMPROVE: '改善する',
};

export interface Experiment {
  id: string;
  title: string;
  description: string;
  plannedMinutes: number;
  actionType: BehaviorSignal;
  reason: string;
  testedHypothesis: string;
}

export interface DomainField {
  id: string;
  name: string;
  icon: string;
  description: string;
  status: 'UNEXPLORED' | 'EXPLORED' | 'TRIED' | 'DIVE_CANDIDATE';
  completedCount: number;
  totalCount: number;
  recommendedSignal: string;
}

export const INITIAL_EXPERIMENTS: Experiment[] = [
  {
    id: 'exp-1',
    title: '好きなゲームのUIを観察する',
    description: '普段遊んでいるゲームの「ホーム画面」を見て、どのボタンが一番目立つように作られているか1つ見つけよう。',
    plannedMinutes: 5,
    actionType: 'ANALYZE',
    reason: '画面の構成や配置の意図に気づく力（UI・情報設計への興味）を確かめるため',
    testedHypothesis: '「なぜ使いやすいのか」を考えることに面白さを感じるか'
  },
  {
    id: 'exp-2',
    title: '3つの図形でミニポスターをつくる',
    description: '丸・三角・四角だけを使って、「楽しさ」や「静けさ」を表現するミニグラフィックを紙かアプリに描いてみよう。',
    plannedMinutes: 10,
    actionType: 'CREATE',
    reason: '限られたルールの中で工夫して形にする楽しさを知るため',
    testedHypothesis: '何もないところから手を使ってアウトプットすることに没頭できるか'
  },
  {
    id: 'exp-3',
    title: 'よく使う2つのアプリ画面を比べる',
    description: 'LINEとInstagramなど、2つのアプリの「検索画面」の違いを1つ見つけてメモしてみよう。',
    plannedMinutes: 5,
    actionType: 'COMPARE',
    reason: '似ているものの微妙な違いや仕組みに目を向ける適性を観察するため',
    testedHypothesis: '違いの理由（ターゲットや用途）を考えるのが自然にできるか'
  }
];

export const INITIAL_DOMAINS: DomainField[] = [
  {
    id: 'f-1',
    name: 'デザイン・UI・情報構造',
    icon: '🎨',
    description: '「どう見せると伝わりやすいか」「なぜ使いやすいのか」を考える分野',
    status: 'TRIED',
    completedCount: 3,
    totalCount: 5,
    recommendedSignal: '分析する / つくる'
  },
  {
    id: 'f-2',
    name: 'ビジネス・仕組み・経済',
    icon: '📈',
    description: '「どうやって利益が出るのか」「なぜ人が集まるのか」を読み解く分野',
    status: 'DIVE_CANDIDATE',
    completedCount: 1,
    totalCount: 4,
    recommendedSignal: '比べる / 改善する'
  },
  {
    id: 'f-3',
    name: 'テクノロジー・プログラミング',
    icon: '💻',
    description: '「コンピュータをどう動かすか」「どう効率化するか」を形にする分野',
    status: 'EXPLORED',
    completedCount: 1,
    totalCount: 6,
    recommendedSignal: '整理する / つくる'
  },
  {
    id: 'f-4',
    name: 'ことば・ストーリー・論理',
    icon: '📝',
    description: '文章や対話で人の心を動かしたり、ロジックを組み立てる分野',
    status: 'UNEXPLORED',
    completedCount: 0,
    totalCount: 4,
    recommendedSignal: '説明する'
  },
  {
    id: 'f-5',
    name: 'モノづくり・建築・空間',
    icon: '📦',
    description: '素材や空間、手に取れる立体物を工夫して作り上げる分野',
    status: 'UNEXPLORED',
    completedCount: 0,
    totalCount: 5,
    recommendedSignal: 'つくる / 改善する'
  },
  {
    id: 'f-6',
    name: 'サイエンス・自然・観察',
    icon: '🔬',
    description: '自然現象やデータの法則性・不思議を探究する分野',
    status: 'UNEXPLORED',
    completedCount: 0,
    totalCount: 4,
    recommendedSignal: '調べる / 分析する'
  }
];
