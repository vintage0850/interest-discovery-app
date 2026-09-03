import { useState } from 'react';
import {
  User,
  MessageCircle,
  Bell,
  Shield,
  FileText,
  ChevronRight,
  X,
  Check,
  Mail,
} from 'lucide-react';

export default function SettingsTab(): JSX.Element {
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

  // データ初期化のプレースホルダー状態
  const [, setCompletedCount] = useState(0);

  return (
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
    </div>
  );
}
